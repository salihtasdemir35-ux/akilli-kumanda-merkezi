package com.akillikumanda.app.feature.discovery

import com.akillikumanda.app.core.model.CommandIds
import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.RemoteProfile
import com.akillikumanda.app.platform.capability.NO_IR_MESSAGE

data class ConnectionOption(val type: ConnectionType, val usable: Boolean, val explanation: String)

const val VEHICLE_GUIDE =
    "Araç sistemleri yalnızca üreticinin resmî dijital anahtar uygulaması veya yetkili API'leri üzerinden kontrol edilebilir. " +
    "Bu uygulama araç anahtarı radyo sinyallerini yakalamaz, kopyalamaz, yeniden oynatmaz ve araç güvenlik sistemlerine müdahale etmez. " +
    "Şu anda desteklenen resmî bir araç entegrasyonu yok; aracınızın üreticisinin uygulamasını kullanın."

object ConnectionPolicy {

    fun catalogGap(category: DeviceCategory): String = when (category) {
        DeviceCategory.AC ->
            "Klimalar her tuşa basıldığında tüm durumu (mod, sıcaklık, fan) tek uzun çerçevede gönderir ve bu çerçeve markaya göre değişir. " +
                "Katalogda henüz doğrulanmış bir klima kodlayıcısı yok; uydurma kod kullanılmaz."
        DeviceCategory.VEHICLE -> VEHICLE_GUIDE
        else -> "Yerel katalogda bu kategori için henüz IR profili yok."
    }

    fun options(category: DeviceCategory, irUsable: Boolean, hasIrProfiles: Boolean): List<ConnectionOption> {
        if (category == DeviceCategory.VEHICLE) {
            return ConnectionType.entries.map { ConnectionOption(it, false, VEHICLE_GUIDE) }
        }
        return listOf(
            ConnectionOption(
                ConnectionType.IR,
                usable = irUsable && hasIrProfiles,
                explanation = when {
                    !irUsable -> NO_IR_MESSAGE
                    !hasIrProfiles -> catalogGap(category)
                    else -> "Telefonun kızılötesi vericisiyle, yerel katalogdaki aday profiller tek tek test edilir."
                },
            ),
            ConnectionOption(ConnectionType.WIFI, false,
                "Wi-Fi entegrasyonları sonraki aşamada eklenecek. Yalnızca belgelenmiş protokoller ve yetkili üretici API'leri kullanılacak; genel ağ taraması yapılmayacak."),
            ConnectionOption(ConnectionType.BLE, false,
                "Bluetooth/BLE entegrasyonları sonraki aşamada eklenecek. Sistem destekli eşleştirme (Companion Device Manager) planlanıyor; konum izni istenmeyecek."),
            ConnectionOption(ConnectionType.VENDOR, false,
                "Bu sürümde üretici entegrasyonu yok. Cihazınızın kendi resmî uygulamasını kullanabilirsiniz."),
            ConnectionOption(ConnectionType.EXTERNAL_ACCESSORY, false,
                "Harici IR aksesuar (USB/Bluetooth IR verici) desteği henüz yok. Eklendiğinde uyumlu aksesuar listesi burada gösterilecek."),
        )
    }

    /**
     * Test için güvenli komutlar, öncelik sırasıyla. Güç en sona konur çünkü cihazı kapatabilir.
     * Klima için güç ve sıcaklık komutları test listesine hiç girmez.
     */
    fun safeTestCommands(profile: RemoteProfile): List<String> {
        val order = when (profile.category) {
            DeviceCategory.AC -> emptyList()
            else -> listOf(CommandIds.MUTE, CommandIds.VOL_UP, CommandIds.VOL_DOWN, CommandIds.MENU, CommandIds.POWER)
        }
        return order.filter { profile.command(it) != null }
    }

    fun testLabel(commandId: String, profile: RemoteProfile): String {
        val base = profile.command(commandId)?.name ?: commandId
        return if (commandId == CommandIds.POWER) "$base (cihazı kapatabilir)" else base
    }
}
