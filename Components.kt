package com.akillikumanda.app.core.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.VerificationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun DeviceCategory.icon(): ImageVector = when (this) {
    DeviceCategory.TV -> Icons.Filled.Tv
    DeviceCategory.AC -> Icons.Filled.AcUnit
    DeviceCategory.AUDIO -> Icons.Filled.Speaker
    DeviceCategory.SATELLITE -> Icons.Filled.SettingsInputAntenna
    DeviceCategory.PROJECTOR -> Icons.Filled.Videocam
    DeviceCategory.MEDIA_PLAYER -> Icons.Filled.PlayCircle
    DeviceCategory.SMART_HOME -> Icons.Filled.Home
    DeviceCategory.VEHICLE -> Icons.Filled.DirectionsCar
    DeviceCategory.OTHER -> Icons.Filled.Devices
}

enum class BannerKind { INFO, WARNING, ERROR, SUCCESS }

@Composable
fun StatusBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier, title: String? = null) {
    val (bg, fg, icon) = when (kind) {
        BannerKind.INFO -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Filled.Info)
        BannerKind.WARNING -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Filled.Warning)
        BannerKind.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.Error)
        BannerKind.SUCCESS -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Filled.CheckCircle)
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun VerificationBadge(status: VerificationStatus) {
    val color = when (status) {
        VerificationStatus.UNVERIFIED -> MaterialTheme.colorScheme.surfaceVariant
        VerificationStatus.USER_CONFIRMED -> MaterialTheme.colorScheme.secondaryContainer
        VerificationStatus.LAB_VERIFIED -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(color = color, shape = RoundedCornerShape(8.dp)) {
        Text(status.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/**
 * Kumanda tuşu. En az 64x56 dp dokunma alanı; ekran okuyucu için tek bir düğme olarak sunulur.
 * [repeatOnHold] açıksa basılı tutuldukça sınırlı sayıda (en fazla 40) tekrar gönderir.
 */
@Composable
fun RemoteKey(
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPress)
    val bg = if (enabled) container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Surface(
        color = bg, contentColor = fg, shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 56.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = if (enabled) label else "$label, bu kumandada yok"
                if (enabled) onClick(label) { latest(); true } else disabled()
            }
            .pointerInput(enabled, repeatOnHold) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    latest()
                    val job = if (repeatOnHold) scope.launch {
                        delay(450)
                        repeat(40) { latest(); delay(220) }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                })
            },
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
