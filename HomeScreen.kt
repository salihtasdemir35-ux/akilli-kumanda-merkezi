package com.akillikumanda.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akillikumanda.app.appContainer
import com.akillikumanda.app.core.database.SavedRemoteEntity
import com.akillikumanda.app.core.designsystem.BannerKind
import com.akillikumanda.app.core.designsystem.SectionTitle
import com.akillikumanda.app.core.designsystem.StatusBanner
import com.akillikumanda.app.core.designsystem.icon
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.data.remote.SavedRemoteRepository
import com.akillikumanda.app.platform.capability.DeviceCapabilities
import com.akillikumanda.app.platform.capability.DeviceCapabilityRepository
import com.akillikumanda.app.platform.capability.NO_IR_MESSAGE
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repo: SavedRemoteRepository, caps: DeviceCapabilityRepository) : ViewModel() {
    val capabilities: DeviceCapabilities = caps.read()
    val remotes: StateFlow<List<SavedRemoteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDiscover: (DeviceCategory?) -> Unit,
    onOpenRemotes: () -> Unit,
    onOpenRemote: (Long) -> Unit,
    onOpenConnections: () -> Unit,
) {
    val c = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(c.savedRemotes, c.capabilities) }
    val remotes by vm.remotes.collectAsStateWithLifecycle()
    val caps = vm.capabilities
    val recent = remember(remotes) { remotes.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(3) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SettingsRemote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Akıllı Kumanda Merkezi", maxLines = 1)
                }
            })
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (caps.irUsable) StatusBanner(
                    "Kızılötesi verici hazır. Desteklenen IR cihazlarını kontrol edebilirsiniz.", BannerKind.SUCCESS,
                ) else StatusBanner(NO_IR_MESSAGE, BannerKind.WARNING, title = "IR verici yok")
            }
            item {
                Button(
                    onClick = { onDiscover(null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Kumanda Bul", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                OutlinedButton(onClick = onOpenRemotes, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.SettingsRemote, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Kumandalarım (${remotes.size})")
                }
            }
            item { SectionTitle("Cihazlar") }
            val tiles = listOf(DeviceCategory.TV, DeviceCategory.AC, DeviceCategory.AUDIO, DeviceCategory.OTHER)
            items(tiles.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cat ->
                        CategoryTile(
                            cat, remotes.count { it.category == cat.name },
                            Modifier.weight(1f),
                        ) { onDiscover(if (cat == DeviceCategory.OTHER) null else cat) }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionTitle("Son kullanılanlar") }
                items(recent, key = { it.id }) { r ->
                    ListItem(
                        headlineContent = { Text(r.name) },
                        supportingContent = { Text(listOfNotNull(r.brand, r.room).joinToString(" · ")) },
                        leadingContent = {
                            Icon((DeviceCategory.fromName(r.category) ?: DeviceCategory.OTHER).icon(), contentDescription = null)
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Kumandayı aç") { onOpenRemote(r.id) },
                        tonalElevation = 1.dp,
                    )
                }
            }
            item { SectionTitle("Desteklenen bağlantı yöntemleri") }
            item {
                ElevatedCard(onClick = onOpenConnections, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MethodRow("Kızılötesi (IR)", if (caps.irUsable) "Kullanılabilir" else "Bu telefonda yok")
                        MethodRow("Wi-Fi / yerel ağ", "Sonraki aşamada")
                        MethodRow("Bluetooth / BLE", "Sonraki aşamada")
                        MethodRow("Araç sistemleri", "Yalnızca resmî entegrasyonlar · henüz yok")
                        Text("Ayrıntılı donanım raporu için dokunun", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodRow(name: String, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTile(cat: DeviceCategory, count: Int, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier.heightIn(min = 112.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(cat.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(cat.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("$count kayıtlı", style = MaterialTheme.typography.bodySmall)
        }
    }
}
