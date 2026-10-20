package com.educationvault.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Royal-blue "vault" look: white cards on a soft grey-blue page.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F54EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6ECFF),
    onPrimaryContainer = Color(0xFF1D39C4),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF1A1F36),
    surface = Color.White,
    onSurface = Color(0xFF1A1F36),
    surfaceVariant = Color(0xFFEEF1F8),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFB8C0D4),
    outlineVariant = Color(0xFFE1E6F2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FA8FF),
    onPrimary = Color(0xFF0B1B5E),
    primaryContainer = Color(0xFF26356F),
    onPrimaryContainer = Color(0xFFDDE5FF),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFE6E9F5),
    surface = Color(0xFF171B2E),
    onSurface = Color(0xFFE6E9F5),
    surfaceVariant = Color(0xFF232842),
    onSurfaceVariant = Color(0xFFA6AEC6),
    outline = Color(0xFF5B6280),
    outlineVariant = Color(0xFF2E3454)
)

@Composable
fun EducationVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
