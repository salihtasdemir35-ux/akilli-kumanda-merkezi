package com.akillikumanda.app.platform.infrared

import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.ProtocolData
import com.akillikumanda.app.core.model.RemoteProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Donanıma en ince erişim katmanı. Android uygulaması AndroidIrHardware, testler sahte sürüm kullanır. */
interface IrHardware {
    /** PackageManager.FEATURE_CONSUMER_IR bildirilmiş mi? */
    val featureDeclared: Boolean
    /** ConsumerIrManager hizmeti alınabildi mi? */
    val serviceAvailable: Boolean
    fun hasEmitter(): Boolean
    /** Boş liste: üretici aralık bildirmiyor (bilinmiyor). */
    fun carrierRanges(): List<IntRange>
    /** Engelleyen çağrı. Hata durumunda istisna fırlatabilir. */
    fun transmit(carrierHz: Int, pattern: IntArray)
}

sealed interface TransmitResult {
    /** Sinyal donanıma iletildi. Hedef cihazın aldığı anlamına GELMEZ. */
    data object Success : TransmitResult
    data object UnsupportedHardware : TransmitResult
    data class UnsupportedFrequency(val requestedHz: Int, val supported: List<IntRange>) : TransmitResult
    data class InvalidProfile(val reason: String) : TransmitResult
    data class PermissionDenied(val detail: String) : TransmitResult
    data class TransmissionError(val detail: String) : TransmitResult
    data object Cancelled : TransmitResult
}

sealed interface ProfileValidation {
    data object Valid : ProfileValidation
    data class Invalid(val reason: String) : ProfileValidation
}

interface InfraredTransmitter {
    fun isAvailable(): Boolean
    fun supportedFrequencies(): List<IntRange>
    fun isFrequencySupported(hz: Int): Boolean
    fun validate(profile: RemoteProfile): ProfileValidation
    suspend fun send(profile: RemoteProfile, commandId: String): TransmitResult
    suspend fun sendRaw(signal: IrSignal): TransmitResult
    /** Kullanıcının başlattığı testi durdurur. Donanıma iletilmiş bir çağrı yarıda kesilemez. */
    fun cancelActiveTest()
}

class DefaultInfraredTransmitter(
    private val hardware: IrHardware,
    private val ioDispatcher: CoroutineDispatcher,
    private val encoder: IrEncoder = IrEncoder(),
) : InfraredTransmitter {

    companion object {
        /** ConsumerIrManager belgelerine göre toplam desen süresi 2 saniyeyi aşmamalı. */
        const val MAX_PATTERN_MICROS = 2_000_000L
        const val MAX_PATTERN_ENTRIES = 1024
    }

    private val mutex = Mutex()
    @Volatile private var activeJob: Job? = null

    override fun isAvailable(): Boolean =
        hardware.serviceAvailable && runCatching { hardware.hasEmitter() }.getOrDefault(false)

    override fun supportedFrequencies(): List<IntRange> =
        runCatching { hardware.carrierRanges() }.getOrDefault(emptyList())

    override fun isFrequencySupported(hz: Int): Boolean {
        val ranges = supportedFrequencies()
        return ranges.isEmpty() || ranges.any { hz in it }
    }

    override fun validate(profile: RemoteProfile): ProfileValidation {
        if (profile.connectionType != ConnectionType.IR) return ProfileValidation.Invalid("Profil IR profili değil")
        if (profile.carrierFrequencyHz !in 20_000..60_000)
            return ProfileValidation.Invalid("Taşıyıcı frekans makul aralıkta değil: ${profile.carrierFrequencyHz} Hz")
        if (profile.commands.isEmpty()) return ProfileValidation.Invalid("Profilde komut yok")
        return ProfileValidation.Valid
    }

    override suspend fun send(profile: RemoteProfile, commandId: String): TransmitResult {
        val v = validate(profile)
        if (v is ProfileValidation.Invalid) return TransmitResult.InvalidProfile(v.reason)
        val cmd = profile.command(commandId)
            ?: return TransmitResult.InvalidProfile("Profilde '$commandId' komutu yok")
        if (cmd.protocolData is ProtocolData.AcFullState)
            return TransmitResult.InvalidProfile("Klima komutu tam durumla gönderilmeli")
        return when (val enc = encoder.encode(profile.protocol, profile.carrierFrequencyHz, cmd.protocolData)) {
            is EncodeResult.Error -> TransmitResult.InvalidProfile(enc.reason)
            is EncodeResult.Ok -> sendRaw(enc.signal)
        }
    }

    override suspend fun sendRaw(signal: IrSignal): TransmitResult {
        if (!isAvailable()) return TransmitResult.UnsupportedHardware
        if (signal.pattern.isEmpty() || signal.pattern.any { it <= 0 })
            return TransmitResult.InvalidProfile("Geçersiz zamanlama dizisi")
        if (signal.pattern.size > MAX_PATTERN_ENTRIES || signal.totalMicros > MAX_PATTERN_MICROS)
            return TransmitResult.InvalidProfile("Desen API sınırını aşıyor (en fazla 2 sn)")
        if (!isFrequencySupported(signal.carrierHz))
            return TransmitResult.UnsupportedFrequency(signal.carrierHz, supportedFrequencies())

        return try {
            mutex.withLock { // eşzamanlı gönderimleri sıraya koy
                activeJob = currentCoroutineContext()[Job]
                try {
                    withContext(ioDispatcher) { hardware.transmit(signal.carrierHz, signal.pattern) }
                    TransmitResult.Success
                } finally {
                    activeJob = null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            TransmitResult.PermissionDenied(e.message ?: "TRANSMIT_IR izni reddedildi")
        } catch (e: Exception) {
            TransmitResult.TransmissionError(e.message ?: e::class.java.simpleName)
        }
    }

    override fun cancelActiveTest() {
        activeJob?.cancel()
    }
}
