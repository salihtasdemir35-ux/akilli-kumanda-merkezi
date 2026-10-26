package com.akillikumanda.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akillikumanda.app.appContainer
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.designsystem.SectionTitle
import com.akillikumanda.app.core.designsystem.StatusBanner
import com.akillikumanda.app.data.catalog.CatalogState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenConnections: () -> Unit) {
    val c = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(c.settings, c.backup, c.catalog) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalog by vm.catalogState.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(resolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(resolver, uri)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Ayarlar") }) }, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Kullanım")
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dokunsal geri bildirim", style = MaterialTheme.typography.bodyLarge)
                    Text("Tuşa basınca kısa titreşim", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.haptics, onCheckedChange = { vm.setHaptics(it) })
            }
            Text("Kumanda Bul: aday başına en fazla ${settings.maxTestsPerCandidate} test sinyali",
                style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = settings.maxTestsPerCandidate.toFloat(), onValueChange = { vm.setMaxTests(it.roundToInt()) },
                valueRange = 1f..5f, steps = 3,
                modifier = Modifier.semantics { stateDescription = "${settings.maxTestsPerCandidate} test" },
            )

            SectionTitle("Donanım ve bağlantılar")
            ListItem(
                headlineContent = { Text("Bağlantı yöntemleri ve donanım raporu") },
                supportingContent = { Text("IR, Wi-Fi, Bluetooth ve araç sistemleri durumu") },
                leadingContent = { Icon(Icons.Filled.Memory, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConnections),
            )

            SectionTitle("Yedekleme")
            Text("Kayıtlı kumandalarınızı bir JSON dosyasına yedekleyin veya geri yükleyin. Geri yüklemede aynı kayıtlar atlanır.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("akilli-kumanda-yedek.json") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(6.dp)); Text("Yedekle")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Geri yükle")
                }
            }

            SectionTitle("Kumanda kataloğu")
            when (val cs = catalog) {
                null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is CatalogState.Broken -> StatusBanner(cs.reason, BannerKind.ERROR, title = "Katalog bozuk")
                is CatalogState.Loaded -> {
                    Text("Sürüm ${cs.version} · ${cs.profiles.size} profil (yerel, çevrim dışı)", style = MaterialTheme.typography.bodyMedium)
                    Text("Katalogdaki profiller \"Doğrulanmadı\" durumundadır: kaynak tablolardan alındı, gerçek cihazda henüz test edilmedi.",
                        style = MaterialTheme.typography.bodySmall)
                    cs.profiles.forEach { p ->
                        Text("• ${p.displayName}\n  Kaynak: ${p.source}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (cs.warnings.isNotEmpty()) StatusBanner(cs.warnings.joinToString("\n"), BannerKind.WARNING, title = "Atlanan profiller")
                }
            }

            SectionTitle("Hakkında")
            Text("Akıllı Kumanda Merkezi 0.1.0 (MVP). İnternet izni istemez; tüm veriler telefonda tutulur.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
