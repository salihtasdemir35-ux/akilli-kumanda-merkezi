package com.akillikumanda.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kontrast: birincil metin/arka plan çiftleri WCAG AA (4.5:1) üzerinde seçildi.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B3D91), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF00695C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDE7), onSecondaryContainer = Color(0xFF00201C),
    tertiaryContainer = Color(0xFFFFDAD6), onTertiaryContainer = Color(0xFF410002),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF), onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF0B3D91), onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFF82D5C8), onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048), onSecondaryContainer = Color(0xFFA0F2E4),
    tertiaryContainer = Color(0xFF93000A), onTertiaryContainer = Color(0xFFFFDAD6),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
)

@Composable
fun AkilliKumandaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
