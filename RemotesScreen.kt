package com.akillikumanda.app.feature.remotes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akillikumanda.app.appContainer
import com.akillikumanda.app.core.database.SavedRemoteEntity
import com.akillikumanda.app.core.designsystem.VerificationBadge
import com.akillikumanda.app.core.designsystem.icon
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.VerificationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotesScreen(onOpenRemote: (Long) -> Unit, onDiscover: () -> Unit) {
    val c = appContainer()
    val vm: RemotesViewModel = viewModel { RemotesViewModel(c.savedRemotes) }
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<SavedRemoteEntity?>(null) }
    var deleting by remember { mutableStateOf<SavedRemoteEntity?>(null) }

    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kumandalarım") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onDiscover, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Kumanda Ekle") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = s.query, onValueChange = vm::setQuery, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Ara (ad, marka, oda)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val cats = s.all.mapNotNull { DeviceCategory.fromName(it.category) }.distinct()
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(s.filter == RemoteFilter.All, { vm.setFilter(RemoteFilter.All) }, { Text("Tümü") }) }
                item { FilterChip(s.filter == RemoteFilter.Favorites, { vm.setFilter(RemoteFilter.Favorites) }, { Text("Favoriler") }) }
                items(cats) { cat ->
                    FilterChip(s.filter == RemoteFilter.Category(cat.name), { vm.setFilter(RemoteFilter.Category(cat.name)) }, { Text(cat.title) })
                }
            }
            if (s.all.isEmpty()) {
                EmptyState("Henüz kayıtlı kumanda yok.", "Kumanda Bul ile cihazınıza uygun profili test edip kaydedin.", onDiscover)
            } else if (s.visible.isEmpty()) {
                EmptyState("Sonuç yok", "Arama veya filtreyi değiştirin.", null)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.visible, key = { it.id }) { r ->
                        RemoteRow(r, onOpen = { onOpenRemote(r.id) }, onFavorite = { vm.toggleFavorite(r) },
                            onRename = { renaming = r }, onDelete = { deleting = r })
                    }
                }
            }
        }
    }

    renaming?.let { r ->
        var name by remember(r.id) { mutableStateOf(r.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Yeniden adlandır") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Ad") }) },
            confirmButton = { TextButton(onClick = { vm.rename(r, name); renaming = null }, enabled = name.isNotBlank()) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } },
        )
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Kumanda silinsin mi?") },
            text = { Text("\"${r.name}\" kalıcı olarak silinecek. Daha önce yedek aldıysanız geri yükleyebilirsiniz.") },
            confirmButton = { TextButton(onClick = { vm.delete(r); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String, onDiscover: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.SettingsRemote, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (onDiscover != null) Button(onClick = onDiscover) { Text("Kumanda Bul") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteRow(
    r: SavedRemoteEntity, onOpen: () -> Unit, onFavorite: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val cat = DeviceCategory.fromName(r.category) ?: DeviceCategory.OTHER
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(cat.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(cat.title, r.brand, r.model, r.room).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                VerificationBadge(VerificationStatus.fromName(r.verification) ?: VerificationStatus.UNVERIFIED)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (r.favorite) "Favorilerden çıkar" else "Favorilere ekle")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Diğer işlemler") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = { menu = false; onRename() },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) })
                    DropdownMenuItem(text = { Text("Sil") }, onClick = { menu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) })
                }
            }
        }
    }
}
