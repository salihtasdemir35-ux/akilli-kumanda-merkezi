package com.akillikumanda.app.feature.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akillikumanda.app.appContainer
import com.akillikumanda.app.core.designsystem.*
import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    initialCategory: DeviceCategory?,
    onOpenRemote: (Long) -> Unit,
    onOpenConnections: () -> Unit,
    onClose: () -> Unit,
) {
    val c = appContainer()
    val vm: DiscoveryViewModel = viewModel {
        DiscoveryViewModel(c.catalog, c.ranker, c.infrared, c.capabilities, c.savedRemotes, c.settings, initialCategory)
    }
    val s by vm.state.collectAsStateWithLifecycle()
    val canGoBack = s.step != DiscoveryStep.CATEGORY && s.step != DiscoveryStep.DONE
    BackHandler(enabled = canGoBack) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kumanda Bul") },
                navigationIcon = {
                    if (canGoBack) IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Önceki adım")
                    }
                },
                actions = {
                    if (s.step != DiscoveryStep.CATEGORY) IconButton(onClick = { vm.stop(); onClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Kumanda Bul'u kapat")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Adım ${s.step.number} / 5 · ${s.step.title}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { s.step.number / 5f }, modifier = Modifier.fillMaxWidth())
            when (s.step) {
                DiscoveryStep.CATEGORY -> CategoryStep(vm::selectCategory)
                DiscoveryStep.DETAILS -> DetailsStep(s, vm)
                DiscoveryStep.CONNECTION -> ConnectionStep(s, vm, onOpenConnections)
                DiscoveryStep.TEST -> TestStep(s, vm)
                DiscoveryStep.NO_MATCH -> NoMatchStep(s, vm, onOpenConnections)
                DiscoveryStep.SAVE -> SaveStep(s, vm, onOpenRemote)
                DiscoveryStep.DONE -> DoneStep(s, onOpenRemote, vm::restart)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryStep(onSelect: (DeviceCategory) -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    Text("Hangi cihazı kontrol etmek istiyorsunuz?", style = MaterialTheme.typography.titleMedium)
    DeviceCategory.entries.forEach { cat ->
        OutlinedCard(onClick = { onSelect(cat) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(cat.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(cat.title, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    TextButton(onClick = { showHelp = !showHelp }) { Text("Cihaz türünü bilmiyorum") }
    if (showHelp) StatusBanner(
        "Orijinal kumandanın tuşlarına bakın: \"CH/P\" ve \"Source\" varsa çoğunlukla TV; sıcaklık (°C) ve mod tuşları varsa klima; " +
            "\"Input/Zoom/Keystone\" varsa projeksiyon; \"Guide/EPG\" varsa uydu alıcısıdır. Emin değilseniz önce \"Televizyon\"u deneyin.",
        BannerKind.INFO, title = "İpucu",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(s: DiscoveryUiState, vm: DiscoveryViewModel) {
    val cat = s.category ?: return
    Text("${cat.title} için marka ve model", style = MaterialTheme.typography.titleMedium)
    s.catalogError?.let { StatusBanner(it, BannerKind.ERROR, title = "Katalog yüklenemedi") }
    if (s.brands.isNotEmpty()) {
        Text("Katalogdaki markalar", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.brands.forEach { b ->
                FilterChip(selected = s.brand.equals(b, true), onClick = { vm.setBrand(if (s.brand.equals(b, true)) "" else b) },
                    label = { Text(b) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    OutlinedTextField(
        value = s.brand, onValueChange = vm::setBrand, label = { Text("Marka") },
        supportingText = { Text("Boş bırakırsanız tüm markaların profilleri denenir") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    OutlinedTextField(
        value = s.model, onValueChange = vm::setModel, label = { Text("Model (isteğe bağlı)") },
        supportingText = { Text("Cihazın arkasındaki etikette yazar. Bilmiyorsanız boş bırakın.") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    OutlinedTextField(
        value = s.customName, onValueChange = vm::setCustomName, label = { Text("Cihaz adı (isteğe bağlı)") },
        placeholder = { Text("Örn. Salon TV") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    if (s.brand.isNotBlank() && s.brands.none { it.equals(s.brand.trim(), true) }) StatusBanner(
        "\"${s.brand.trim()}\" için yerel katalogda profil yok. Devam ederseniz sonuç bulunamayabilir; markayı boş bırakarak genel profilleri deneyebilirsiniz.",
        BannerKind.WARNING,
    )
    Button(onClick = vm::continueFromDetails, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Devam") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionStep(s: DiscoveryUiState, vm: DiscoveryViewModel, onOpenConnections: () -> Unit) {
    Text("İletişim yöntemi", style = MaterialTheme.typography.titleMedium)
    Text("Yalnızca bu telefonda ve bu sürümde gerçekten çalışan yöntemler seçilebilir.", style = MaterialTheme.typography.bodyMedium)
    if (s.category == DeviceCategory.VEHICLE) StatusBanner(VEHICLE_GUIDE, BannerKind.INFO, title = "Araç sistemleri")
    s.options.forEach { o ->
        val icon = when (o.type) {
            ConnectionType.IR -> Icons.Filled.SettingsRemote
            ConnectionType.WIFI -> Icons.Filled.Wifi
            ConnectionType.BLE -> Icons.Filled.Bluetooth
            ConnectionType.VENDOR -> Icons.Filled.Business
            ConnectionType.EXTERNAL_ACCESSORY -> Icons.Filled.Usb
        }
        OutlinedCard(
            onClick = { vm.chooseConnection(o) }, enabled = o.usable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.type.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (o.usable) "Kullanılabilir" else "Kullanılamıyor", style = MaterialTheme.typography.labelMedium,
                        color = if (o.usable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(o.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (s.options.none { it.usable }) {
        StatusBanner("Bu cihaz için şu anda kullanılabilir bir yöntem yok. Hiçbir şey kaydedilmedi.", BannerKind.WARNING)
        OutlinedButton(onClick = onOpenConnections, modifier = Modifier.fillMaxWidth()) { Text("Donanım raporunu görüntüle") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestStep(s: DiscoveryUiState, vm: DiscoveryViewModel) {
    val cand = s.current ?: return
    val p = cand.profile
    Text("Aday ${s.index + 1} / ${s.candidates.size}", style = MaterialTheme.typography.titleMedium)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerificationBadge(p.verificationStatus)
                Spacer(Modifier.width(8.dp))
                Text("${p.carrierFrequencyHz / 1000} kHz", style = MaterialTheme.typography.labelMedium)
            }
            Text("Sıralama nedeni: " + cand.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text("Sıralama puanı yalnızca deneme sırasını belirler; uyumluluğu kanıtlamaz.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val testCmds = ConnectionPolicy.safeTestCommands(p)
    if (testCmds.isEmpty()) {
        StatusBanner("Bu profilde güvenli bir test komutu yok.", BannerKind.WARNING)
    } else {
        Text("Test komutu", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            testCmds.forEach { id ->
                FilterChip(selected = s.testCommandId == id, onClick = { vm.chooseTestCommand(id) },
                    label = { Text(ConnectionPolicy.testLabel(id, p)) }, enabled = !s.sending,
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    s.status?.let {
        StatusBanner(it.text, it.kind, title = it.title,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Text("Bu adayda gönderim: ${s.sendsOnCurrent} / ${s.maxSends}", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (s.sending) {
            Button(onClick = vm::stop, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.Stop, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Durdur")
            }
        } else {
            Button(onClick = vm::test, enabled = s.canSendMore && s.testCommandId != null,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Sensors, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Test et")
            }
        }
        OutlinedButton(onClick = vm::nextCandidate, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Sonraki kod") }
    }
    if (!s.canSendMore && !s.signalSentOnCurrent) StatusBanner(
        "Bu aday için deneme sınırına ulaşıldı. \"Sonraki kod\" ile devam edin.", BannerKind.INFO)
    Text("Cihaz tepki verdi mi?", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = vm::worked, enabled = s.signalSentOnCurrent && !s.sending,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Çalıştı")
        }
        OutlinedButton(onClick = vm::nextCandidate, enabled = s.signalSentOnCurrent && !s.sending,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Çalışmadı")
        }
    }
    if (!s.signalSentOnCurrent) Text("\"Çalıştı/Çalışmadı\" ancak sinyal gerçekten gönderildikten sonra seçilebilir.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NoMatchStep(s: DiscoveryUiState, vm: DiscoveryViewModel, onOpenConnections: () -> Unit) {
    StatusBanner(
        (s.noMatchReason ?: "Uygun profil bulunamadı.") + " Hiçbir kumanda kaydedilmedi.",
        BannerKind.WARNING, title = "Eşleşme bulunamadı",
    )
    Text("Şunları deneyebilirsiniz:", style = MaterialTheme.typography.titleSmall)
    Text("• Marka ve model bilgisini cihaz etiketinden yeniden kontrol edin.\n" +
        "• Markayı boş bırakarak tüm profilleri deneyin.\n" +
        "• Telefonu cihaza daha yakın ve doğrudan doğrultun; güneş ışığı IR'yi zayıflatabilir.\n" +
        "• Diğer bağlantı yöntemlerinin durumuna bakın.", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = vm::editDetails, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Marka ve modeli düzelt") }
    OutlinedButton(onClick = onOpenConnections, modifier = Modifier.fillMaxWidth()) { Text("Bağlantı yöntemlerini görüntüle") }
    TextButton(onClick = vm::restart, modifier = Modifier.fillMaxWidth()) { Text("Baştan başla") }
}

@Composable
private fun SaveStep(s: DiscoveryUiState, vm: DiscoveryViewModel, onOpenRemote: (Long) -> Unit) {
    val p = s.confirmed ?: return
    StatusBanner("Profil sizin onayınızla doğrulandı: ${p.displayName}", BannerKind.SUCCESS, title = "Çalışan profil")
    OutlinedTextField(value = s.saveName, onValueChange = vm::setSaveName, label = { Text("Kumanda adı") },
        singleLine = true, modifier = Modifier.fillMaxWidth(), isError = s.saveError != null)
    OutlinedTextField(value = s.room, onValueChange = vm::setRoom, label = { Text("Oda / kullanım alanı (isteğe bağlı)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = s.note, onValueChange = vm::setNote, label = { Text("Not (isteğe bağlı)") },
        modifier = Modifier.fillMaxWidth(), minLines = 2)
    s.saveError?.let { StatusBanner(it, BannerKind.ERROR) }
    s.duplicateId?.let { id -> OutlinedButton(onClick = { onOpenRemote(id) }, modifier = Modifier.fillMaxWidth()) { Text("Mevcut kumandayı aç") } }
    Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Filled.Save, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Kumandayı kaydet")
    }
}

@Composable
private fun DoneStep(s: DiscoveryUiState, onOpenRemote: (Long) -> Unit, onRestart: () -> Unit) {
    StatusBanner("\"${s.saveName}\" Kumandalarım'a kaydedildi.", BannerKind.SUCCESS, title = "Kaydedildi")
    s.savedId?.let { id ->
        Button(onClick = { onOpenRemote(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Kumandayı aç") }
    }
    OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Başka bir kumanda bul") }
}
