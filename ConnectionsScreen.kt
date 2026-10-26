package com.akillikumanda.app.feature.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akillikumanda.app.appContainer
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.designsystem.SectionTitle
import com.akillikumanda.app.core.designsystem.StatusBanner
import com.akillikumanda.app.feature.discovery.VEHICLE_GUIDE
import com.akillikumanda.app.platform.capability.NO_IR_MESSAGE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(onBack: () -> Unit) {
    val c = appContainer()
    var caps by remember { mutableStateOf(c.capabilities.read()) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Bağlantılar ve donanım") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") } },
            actions = { TextButton(onClick = { caps = c.capabilities.read() }) { Text("Yenile") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Kızılötesi (IR)")
            if (!caps.irUsable) StatusBanner(NO_IR_MESSAGE, BannerKind.WARNING)
            Fact("Sistem IR özelliği bildiriyor", yesNo(caps.irFeatureDeclared))
            Fact("IR hizmeti (ConsumerIrManager)", yesNo(caps.irServiceAvailable))
            Fact("IR verici mevcut", yesNo(caps.irEmitter))
            Fact("TRANSMIT_IR izni", if (caps.irPermissionGranted) "Verildi" else "Verilmedi")
            Fact("Taşıyıcı frekanslar", when {
                !caps.irEmitter -> "—"
                caps.carrierRanges.isEmpty() -> "Üretici bildirmiyor"
                else -> caps.carrierRanges.joinToString { r ->
                    if (r.first == r.last) "${r.first / 1000.0} kHz" else "${r.first / 1000.0}–${r.last / 1000.0} kHz"
                }
            })
            Fact("Harici aksesuar gerekli mi", if (caps.irNeedsExternalAccessory) "Evet (henüz desteklenmiyor)" else "Hayır")

            SectionTitle("Bluetooth / BLE")
            Fact("Bluetooth donanımı", yesNo(caps.bluetoothHardware))
            Fact("BLE donanımı", yesNo(caps.bleHardware))
            Fact("Bluetooth açık", when (caps.bluetoothEnabled) { true -> "Evet"; false -> "Hayır"; null -> "Okunamadı" })
            Fact("Gerekli izinler", "Bu sürümde Bluetooth entegrasyonu olmadığı için izin istenmiyor")
            Text("Bluetooth/BLE cihaz entegrasyonları sonraki aşamada eklenecek. Konum izni istenmeyecek.",
                style = MaterialTheme.typography.bodySmall)

            SectionTitle("Wi-Fi / yerel ağ")
            Fact("Wi-Fi donanımı", yesNo(caps.wifiHardware))
            Fact("Entegrasyon", "Henüz yok")
            Text("Wi-Fi kontrolü yalnızca belgelenmiş protokoller ve yetkili üretici API'leri ile eklenecek; genel ağ taraması yapılmayacak.",
                style = MaterialTheme.typography.bodySmall)

            SectionTitle("Bu sürümde kullanılabilen yöntemler")
            Text(if (caps.usableMethods.isEmpty()) "Hiçbiri. Kayıtlı kumandalarınızı görüntüleyebilir, yedekleyebilirsiniz; sinyal gönderilemez."
                else caps.usableMethods.joinToString { it.title }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

            SectionTitle("Araç sistemleri")
            StatusBanner(VEHICLE_GUIDE, BannerKind.INFO)
        }
    }
}

private fun yesNo(b: Boolean) = if (b) "Evet" else "Hayır"

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
