package com.akillikumanda.app.feature.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akillikumanda.app.core.designsystem.RemoteKey
import com.akillikumanda.app.core.model.CommandIds
import com.akillikumanda.app.core.model.RemoteProfile
import com.akillikumanda.app.core.model.RepeatPolicy

/** TV kumanda düzeni. Profilde olmayan tuşlar devre dışı ve ekran okuyucuya "bu kumandada yok" diye bildirilir. */
@Composable
fun TvRemotePanel(profile: RemoteProfile, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    @Composable
    fun Key(id: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, m: Modifier = Modifier, danger: Boolean = false) {
        val cmd = profile.command(id)
        if (danger) RemoteKey(
            label = label, icon = icon, enabled = cmd != null, onPress = { onSend(id) }, modifier = m,
            repeatOnHold = cmd?.repeatPolicy == RepeatPolicy.HOLD_REPEAT,
            container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError,
        ) else RemoteKey(
            label = label, icon = icon, enabled = cmd != null, onPress = { onSend(id) }, modifier = m,
            repeatOnHold = cmd?.repeatPolicy == RepeatPolicy.HOLD_REPEAT,
        )
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Key(CommandIds.POWER, "Güç", Icons.Filled.PowerSettingsNew, Modifier.weight(1f), danger = true)
            Key(CommandIds.MUTE, "Sessiz", Icons.AutoMirrored.Filled.VolumeOff, Modifier.weight(1f))
            Key(CommandIds.SOURCE, "Kaynak", Icons.AutoMirrored.Filled.Input, Modifier.weight(1f))
        }
        // Yön tuşları
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(CommandIds.UP, "Yukarı", Icons.Filled.KeyboardArrowUp, Modifier.widthIn(min = 88.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Key(CommandIds.LEFT, "Sol", Icons.Filled.KeyboardArrowLeft, Modifier.widthIn(min = 80.dp))
                RemoteKey(label = "OK", onPress = { onSend(CommandIds.OK) }, enabled = profile.command(CommandIds.OK) != null,
                    modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 72.dp),
                    container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
                Key(CommandIds.RIGHT, "Sağ", Icons.Filled.KeyboardArrowRight, Modifier.widthIn(min = 80.dp))
            }
            Key(CommandIds.DOWN, "Aşağı", Icons.Filled.KeyboardArrowDown, Modifier.widthIn(min = 88.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(CommandIds.VOL_UP, "Ses +", Icons.Filled.Add, Modifier.fillMaxWidth())
                Text("Ses", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                Key(CommandIds.VOL_DOWN, "Ses −", Icons.Filled.Remove, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(CommandIds.CH_UP, "Kanal +", Icons.Filled.KeyboardArrowUp, Modifier.fillMaxWidth())
                Text("Kanal", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                Key(CommandIds.CH_DOWN, "Kanal −", Icons.Filled.KeyboardArrowDown, Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Key(CommandIds.BACK, "Geri", Icons.AutoMirrored.Filled.ArrowBack, Modifier.weight(1f))
            Key(CommandIds.HOME, "Ana ekran", Icons.Filled.Home, Modifier.weight(1f))
            Key(CommandIds.MENU, "Menü", Icons.Filled.Menu, Modifier.weight(1f))
        }
    }
}
