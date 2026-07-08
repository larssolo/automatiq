package com.vibeactions.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.vibeactions.ui.common.ThemeSettings

@Composable
fun VibeActionsTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = ThemeSettings.accentColor,
        onPrimary = OnPrimary,
        background = Background,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        error = ErrorRed,
        outline = Outline
    )
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
