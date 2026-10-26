package com.akillikumanda.app.feature.ac

/**
 * Klima kumandası "tahmini durum"u. Klima gerçek durumunu geri bildirmez;
 * bu değerler yalnızca son gönderilen komutu yansıtır.
 */
enum class AcMode(val title: String) { COOL("Soğutma"), HEAT("Isıtma"), AUTO("Otomatik"), DRY("Nem alma"), FAN("Fan") }
enum class AcFan(val title: String) { AUTO("Otomatik"), LOW("Düşük"), MEDIUM("Orta"), HIGH("Yüksek") }

data class AcState(
    val power: Boolean = false,
    val temperatureC: Int = 24,
    val mode: AcMode = AcMode.COOL,
    val fan: AcFan = AcFan.AUTO,
    val swing: Boolean = false,
    val timerHours: Int = 0,
) {
    companion object {
        const val MIN_TEMP = 16
        const val MAX_TEMP = 30
        const val MAX_TIMER = 12
    }

    fun togglePower() = copy(power = !power)
    fun tempUp() = copy(temperatureC = (temperatureC + 1).coerceAtMost(MAX_TEMP))
    fun tempDown() = copy(temperatureC = (temperatureC - 1).coerceAtLeast(MIN_TEMP))
    fun nextMode() = copy(mode = AcMode.entries[(mode.ordinal + 1) % AcMode.entries.size])
    fun nextFan() = copy(fan = AcFan.entries[(fan.ordinal + 1) % AcFan.entries.size])
    fun toggleSwing() = copy(swing = !swing)
    fun nextTimer() = copy(timerHours = if (timerHours >= MAX_TIMER) 0 else timerHours + 1)
}

/**
 * Bir klima markasının tam durum çerçevesini üreten kodlayıcı.
 * Her marka/model ailesi ayrı bir kodlayıcı gerektirir; MVP'de doğrulanmış kodlayıcı yoktur.
 */
interface AcStateCodec {
    val id: String
    val minTemp: Int
    val maxTemp: Int
    val supportedModes: Set<AcMode>
    /** Mikro saniye cinsinden açık/kapalı dizisi döndürür. */
    fun encode(state: AcState): IntArray
}

object AcCodecRegistry {
    private val codecs = mutableMapOf<String, AcStateCodec>()
    fun register(codec: AcStateCodec) { codecs[codec.id] = codec }
    fun find(id: String): AcStateCodec? = codecs[id]
}
