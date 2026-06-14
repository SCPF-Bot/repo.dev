package com.mlbb.assistant.presentation.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = MLBBGold,
    onPrimary        = SurfaceDark,
    primaryContainer = MLBBGoldDark,
    secondary        = MLBBTeal,
    onSecondary      = SurfaceDark,
    tertiary         = MLBBRed,
    background       = SurfaceDark,
    surface          = SurfaceMid,
    surfaceVariant   = SurfaceCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error            = ErrorRed
)

@Composable
fun MLBBAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = MLBBTypography,
        content     = content
    )
}
