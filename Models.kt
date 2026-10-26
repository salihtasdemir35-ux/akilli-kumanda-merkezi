package com.akillikumanda.app.core.model

/** Cihaz kategorileri. Simgeler arayüz katmanında eşlenir (core/designsystem). */
enum class DeviceCategory(val title: String) {
    TV("Televizyon"),
    AC("Klima"),
    AUDIO("Ses Sistemi"),
    SATELLITE("Uydu Alıcısı"),
    PROJECTOR("Projeksiyon"),
    MEDIA_PLAYER("Medya Oynatıcı"),
    SMART_HOME("Akıllı Ev"),
    VEHICLE("Araç Sistemi"),
    OTHER("Diğer Cihazlar");

    companion object {
        fun fromName(name: String): DeviceCategory? = entries.firstOrNull { it.name == name }
    }
}

/** Bağlantı yöntemleri birbirinin yerine kullanılmaz; her biri ayrı bir yol. */
enum class ConnectionType(val title: String) {
    IR("Kızılötesi (IR)"),
    WIFI("Wi-Fi / Yerel ağ"),
    BLE("Bluetooth / BLE"),
    VENDOR("Üretici entegrasyonu"),
    EXTERNAL_ACCESSORY("Harici aksesuar");

    companion object {
        fun fromName(name: String): ConnectionType? = entries.firstOrNull { it.name == name }
    }
}

enum class VerificationStatus(val title: String) {
    /** Kaynaktan alındı, ekibimizce gerçek cihazda doğrulanmadı. */
    UNVERIFIED("Doğrulanmadı"),
    /** Bu telefonda kullanıcı "Çalıştı" dedi. Yalnızca yerel kayıtta tutulur. */
    USER_CONFIRMED("Kullanıcı onayladı"),
    /** Gerçek cihazda test edildi (katalog yazarı tarafından). */
    LAB_VERIFIED("Gerçek cihazda doğrulandı");

    companion object {
        fun fromName(name: String): VerificationStatus? = entries.firstOrNull { it.name == name }
    }
}

/** IR protokolleri. Zamanlama sabitleri platform/infrared/IrEncoder içindedir. */
enum class IrProtocol(val title: String, val defaultCarrierHz: Int) {
    NEC("NEC (32 bit)", 38_000),
    SAMSUNG32("Samsung (32 bit)", 38_000),
    SONY_SIRC12("Sony SIRC (12 bit)", 40_000),
    PHILIPS_RC5("Philips RC5", 36_000),
    RAW("Ham zamanlama dizisi", 38_000);

    companion object {
        fun fromName(name: String): IrProtocol? = entries.firstOrNull { it.name == name }
    }
}

enum class RepeatPolicy { ONCE, HOLD_REPEAT }

/** Komutun hangi cihaz durumunda anlamlı olduğu (örn. TV kapalıyken ses tuşu işe yaramaz). */
enum class DeviceStateRequirement { ANY, ON, OFF }

/** Bilinen komut kimlikleri. Katalogda bilinmeyen kimlikler de taşınabilir (id String). */
object CommandIds {
    const val POWER = "POWER"
    const val VOL_UP = "VOL_UP"
    const val VOL_DOWN = "VOL_DOWN"
    const val CH_UP = "CH_UP"
    const val CH_DOWN = "CH_DOWN"
    const val MUTE = "MUTE"
    const val SOURCE = "SOURCE"
    const val MENU = "MENU"
    const val UP = "UP"
    const val DOWN = "DOWN"
    const val LEFT = "LEFT"
    const val RIGHT = "RIGHT"
    const val OK = "OK"
    const val BACK = "BACK"
    const val HOME = "HOME"
    /** Klima: tüm durumu tek çerçevede gönderen komut. */
    const val AC_STATE = "AC_STATE"
}

/**
 * Protokol verisi. Her protokol kendi ihtiyacına göre modellenir:
 *  - Scalar: NEC/Samsung/SIRC/RC5 gibi sabit uzunluklu kod değeri.
 *  - Raw: doğrudan açık/kapalı mikro saniye dizisi.
 *  - AcFullState: klima için tam durum çerçevesi; çerçeve, durumdan bir kodlayıcı ile üretilir.
 */
sealed interface ProtocolData {
    data class Scalar(val value: Long, val bits: Int) : ProtocolData
    data class Raw(val patternMicros: List<Int>) : ProtocolData
    data class AcFullState(val codecId: String) : ProtocolData
}

data class RemoteCommand(
    val id: String,
    val name: String,
    val description: String,
    val protocolData: ProtocolData,
    val repeatPolicy: RepeatPolicy,
    val supportedDeviceState: DeviceStateRequirement,
)

data class RemoteProfile(
    val id: String,
    val category: DeviceCategory,
    val brand: String,
    val model: String?,
    val modelPrefixes: List<String>,
    val protocol: IrProtocol,
    val connectionType: ConnectionType,
    val carrierFrequencyHz: Int,
    val commands: List<RemoteCommand>,
    val source: String,
    val license: String,
    val verificationStatus: VerificationStatus,
    val version: Int,
) {
    fun command(id: String): RemoteCommand? = commands.firstOrNull { it.id == id }
    val displayName: String get() = buildString {
        append(brand); append(" "); append(category.title)
        if (!model.isNullOrBlank()) { append(" "); append(model) }
        append(" · "); append(protocol.title)
    }
}
