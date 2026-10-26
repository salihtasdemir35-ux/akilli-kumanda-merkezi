package com.akillikumanda.app.feature.ac

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.designsystem.RemoteKey
import com.akillikumanda.app.core.designsystem.StatusBanner
import com.akillikumanda.app.feature.discovery.ConnectionPolicy
import com.akillikumanda.app.core.model.DeviceCategory

/**
 * Klima paneli. Her tuş yeni bir tam durum hesaplar ve [onSend] ile gönderir; durum ancak gönderim başarılıysa güncellenir.
 * Gösterge "tahmini durum"dur: klima gerçek durumunu telefona bildirmez.
 */
@Composable
fun AcRemotePanel(state: AcState, everSent: Boolean, codecAvailable: Boolean, onSend: (AcState) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!codecAvailable) StatusBanner(ConnectionPolicy.catalogGap(DeviceCategory.AC), BannerKind.WARNING, title = "Klima kodlayıcısı yok")
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (everSent) "Tahmini durum · son gönderilen komut" else "Henüz komut gönderilmedi · varsayılan değerler",
                    style = MaterialTheme.typography.labelLarge)
                Text("${state.temperatureC}°C", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "Tahmini sıcaklık ${state.temperatureC} derece" })
                Text(listOf(if (state.power) "Açık" else "Kapalı", state.mode.title, "Fan: ${state.fan.title}",
                    if (state.swing) "Salınım açık" else "Salınım kapalı",
                    if (state.timerHours > 0) "Zamanlayıcı ${state.timerHours} sa" else "Zamanlayıcı yok").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium)
                Text("Klima gerçek durumunu bildirmez; değerler doğrulanmış ölçüm değildir.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey("Aç/Kapat", { onSend(state.togglePower()) }, Modifier.weight(1f), Icons.Filled.PowerSettingsNew, codecAvailable,
                container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError)
            RemoteKey("Sıcaklık −", { onSend(state.tempDown()) }, Modifier.weight(1f), Icons.Filled.Remove, codecAvailable && state.power)
            RemoteKey("Sıcaklık +", { onSend(state.tempUp()) }, Modifier.weight(1f), Icons.Filled.Add, codecAvailable && state.power)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey("Mod", { onSend(state.nextMode()) }, Modifier.weight(1f), Icons.Filled.Tune, codecAvailable && state.power)
            RemoteKey("Fan hızı", { onSend(state.nextFan()) }, Modifier.weight(1f), Icons.Filled.Air, codecAvailable && state.power)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey("Salınım", { onSend(state.toggleSwing()) }, Modifier.weight(1f), Icons.Filled.SwapVert, codecAvailable && state.power)
            RemoteKey("Zamanlayıcı", { onSend(state.nextTimer()) }, Modifier.weight(1f), Icons.Filled.Timer, codecAvailable)
        }
    }
}
