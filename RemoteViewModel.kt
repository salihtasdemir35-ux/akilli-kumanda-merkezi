package com.akillikumanda.app.feature.remotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akillikumanda.app.core.database.SavedRemoteEntity
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.model.CommandIds
import com.akillikumanda.app.core.model.ProtocolData
import com.akillikumanda.app.core.model.RemoteProfile
import com.akillikumanda.app.core.model.VerificationStatus
import com.akillikumanda.app.data.remote.SavedRemoteRepository
import com.akillikumanda.app.data.settings.AppSettings
import com.akillikumanda.app.data.settings.SettingsRepository
import com.akillikumanda.app.feature.ac.AcCodecRegistry
import com.akillikumanda.app.feature.ac.AcState
import com.akillikumanda.app.feature.discovery.ConnectionPolicy
import com.akillikumanda.app.feature.discovery.StatusMessage
import com.akillikumanda.app.platform.infrared.InfraredTransmitter
import com.akillikumanda.app.platform.infrared.IrSignal
import com.akillikumanda.app.platform.infrared.TransmitResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class RetestPhase { HIDDEN, READY, SENT }

data class RemoteUiState(
    val loading: Boolean = true,
    val entity: SavedRemoteEntity? = null,
    val profile: RemoteProfile? = null,
    val status: StatusMessage? = null,
    val acState: AcState = AcState(),
    val acStateEverSent: Boolean = false,
    val retest: RetestPhase = RetestPhase.HIDDEN,
    val retestCommand: String? = null,
)

class RemoteViewModel(
    private val id: Long,
    private val repo: SavedRemoteRepository,
    private val infrared: InfraredTransmitter,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val busy = AtomicBoolean(false)
    private var markedUsed = false

    init {
        viewModelScope.launch {
            repo.observe(id).collect { e ->
                _state.update { it.copy(loading = false, entity = e, profile = e?.let(repo::profileOf)) }
            }
        }
    }

    val acCodecAvailable: Boolean
        get() = acCodecId()?.let { AcCodecRegistry.find(it) } != null

    private fun acCodecId(): String? =
        (_state.value.profile?.command(CommandIds.AC_STATE)?.protocolData as? ProtocolData.AcFullState)?.codecId

    /** Basılı tutma tekrarlarında önceki gönderim bitmeden yenisi kuyruğa alınmaz. */
    fun send(commandId: String) {
        val p = _state.value.profile ?: return
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val r = infrared.send(p, commandId)
                val label = p.command(commandId)?.name ?: commandId
                onResult(r, "Sinyal gönderildi: $label — cihaz tepkisini kontrol edin.")
            } finally { busy.set(false) }
        }
    }

    fun sendAc(newState: AcState) {
        val p = _state.value.profile ?: return
        val codec = acCodecId()?.let { AcCodecRegistry.find(it) }
        if (codec == null) {
            _state.update { it.copy(status = StatusMessage("Bu klima profili için doğrulanmış kodlayıcı yok; sinyal gönderilmedi.", BannerKind.ERROR)) }
            return
        }
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val r = infrared.sendRaw(IrSignal(p.carrierFrequencyHz, codec.encode(newState)))
                if (r == TransmitResult.Success) _state.update { it.copy(acState = newState, acStateEverSent = true) }
                onResult(r, "Tam durum gönderildi — klimanın tepkisini kontrol edin.")
            } finally { busy.set(false) }
        }
    }

    private suspend fun onResult(r: TransmitResult, successText: String) {
        val msg = when (r) {
            TransmitResult.Success -> {
                if (!markedUsed) { markedUsed = true; repo.markUsed(id) }
                StatusMessage(successText, BannerKind.INFO)
            }
            TransmitResult.UnsupportedHardware -> StatusMessage("Bu telefonda kullanılabilir IR verici yok.", BannerKind.ERROR)
            is TransmitResult.UnsupportedFrequency -> StatusMessage("Telefon ${r.requestedHz} Hz frekansını desteklemiyor.", BannerKind.ERROR)
            is TransmitResult.InvalidProfile -> StatusMessage(r.reason, BannerKind.ERROR)
            is TransmitResult.PermissionDenied -> StatusMessage(r.detail, BannerKind.ERROR)
            is TransmitResult.TransmissionError -> StatusMessage("Gönderim başarısız: ${r.detail}", BannerKind.ERROR)
            TransmitResult.Cancelled -> StatusMessage("İptal edildi", BannerKind.INFO)
        }
        _state.update { it.copy(status = msg) }
    }

    fun toggleFavorite() {
        val e = _state.value.entity ?: return
        viewModelScope.launch { repo.setFavorite(e.id, !e.favorite) }
    }

    fun openRetest() {
        val p = _state.value.profile ?: return
        _state.update { it.copy(retest = RetestPhase.READY, retestCommand = ConnectionPolicy.safeTestCommands(p).firstOrNull()) }
    }

    fun sendRetest() {
        val p = _state.value.profile ?: return
        val cmd = _state.value.retestCommand ?: return
        viewModelScope.launch {
            val r = infrared.send(p, cmd)
            if (r == TransmitResult.Success) _state.update { it.copy(retest = RetestPhase.SENT) }
            else { onResult(r, ""); _state.update { it.copy(retest = RetestPhase.HIDDEN) } }
        }
    }

    fun finishRetest(worked: Boolean) {
        viewModelScope.launch {
            repo.setVerification(id, if (worked) VerificationStatus.USER_CONFIRMED else VerificationStatus.UNVERIFIED)
            _state.update {
                it.copy(retest = RetestPhase.HIDDEN, status = if (worked)
                    StatusMessage("Kumanda yeniden doğrulandı.", BannerKind.SUCCESS)
                else StatusMessage("Kumanda \"Doğrulanmadı\" olarak işaretlendi. Kumanda Bul ile başka bir profil deneyebilirsiniz.", BannerKind.WARNING))
            }
        }
    }

    fun dismissRetest() = _state.update { it.copy(retest = RetestPhase.HIDDEN) }

    override fun onCleared() { infrared.cancelActiveTest() }
}
