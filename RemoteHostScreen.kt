package com.akillikumanda.app.feature.remotes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akillikumanda.app.appContainer
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.designsystem.StatusBanner
import com.akillikumanda.app.core.designsystem.VerificationBadge
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.VerificationStatus
import com.akillikumanda.app.feature.ac.AcRemotePanel
import com.akillikumanda.app.feature.discovery.ConnectionPolicy
import com.akillikumanda.app.feature.tv.TvRemotePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteHostScreen(id: Long, onBack: () -> Unit, onDiscover: (DeviceCategory) -> Unit) {
    val c = appContainer()
    val vm: RemoteViewModel = viewModel(key = "remote-$id") { RemoteViewModel(id, c.savedRemotes, c.infrared, c.settings) }
    val s by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val e = s.entity
    val p = s.profile

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e?.name ?: "Kumanda", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") } },
                actions = {
                    if (e != null) {
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(if (e.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (e.favorite) "Favorilerden çıkar" else "Favorilere ekle")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Diğer işlemler") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Yeniden test et") }, enabled = p != null && ConnectionPolicy.safeTestCommands(p).isNotEmpty(),
                                    onClick = { menu = false; vm.openRetest() })
                                DropdownMenuItem(text = { Text("Başka profil bul") }, onClick = {
                                    menu = false; onDiscover(DeviceCategory.fromName(e.category) ?: DeviceCategory.OTHER)
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                s.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                e == null -> StatusBanner("Bu kumanda bulunamadı; silinmiş olabilir.", BannerKind.ERROR)
                p == null -> StatusBanner("Kayıtlı profil verisi okunamadı. Kumandayı silip Kumanda Bul ile yeniden ekleyin.", BannerKind.ERROR)
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerificationBadge(VerificationStatus.fromName(e.verification) ?: VerificationStatus.UNVERIFIED)
                        Text(listOfNotNull(e.brand, e.model, e.room, p.protocol.title).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    s.status?.let {
                        StatusBanner(it.text, it.kind, title = it.title, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    val withHaptic: (() -> Unit) -> Unit = { action ->
                        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        action()
                    }
                    if (p.category == DeviceCategory.AC) {
                        AcRemotePanel(s.acState, s.acStateEverSent, vm.acCodecAvailable) { st -> withHaptic { vm.sendAc(st) } }
                    } else {
                        TvRemotePanel(p, onSend = { cmd -> withHaptic { vm.send(cmd) } })
                    }
                    e.note?.let { Text("Not: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (s.retest != RetestPhase.HIDDEN && p != null) {
        val cmdLabel = s.retestCommand?.let { ConnectionPolicy.testLabel(it, p) } ?: "-"
        AlertDialog(
            onDismissRequest = vm::dismissRetest,
            title = { Text("Yeniden test") },
            text = {
                Text(if (s.retest == RetestPhase.READY)
                    "Telefonu cihaza doğrultun. \"$cmdLabel\" komutu bir kez gönderilecek."
                else "Sinyal gönderildi; cihaz tepki verdi mi?")
            },
            confirmButton = {
                if (s.retest == RetestPhase.READY) TextButton(onClick = vm::sendRetest) { Text("Gönder") }
                else TextButton(onClick = { vm.finishRetest(true) }) { Text("Çalıştı") }
            },
            dismissButton = {
                if (s.retest == RetestPhase.READY) TextButton(onClick = vm::dismissRetest) { Text("Vazgeç") }
                else TextButton(onClick = { vm.finishRetest(false) }) { Text("Çalışmadı") }
            },
        )
    }
}
