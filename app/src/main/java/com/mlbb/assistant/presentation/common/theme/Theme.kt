package com.mlbb.assistant.presentation.common.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Branded dark colour scheme — fallback on API < 31 or when dynamic color is explicitly disabled.
private val MLBBDarkColorScheme = darkColorScheme(
    primary              = MLBBGold,
    onPrimary            = Color(0xFF1A1400),
    primaryContainer     = Color(0xFF3B2E00),
    onPrimaryContainer   = MLBBGold,

    secondary            = MLBBTeal,
    onSecondary          = Color(0xFF001E1D),
    secondaryContainer   = Color(0xFF00302E),
    onSecondaryContainer = MLBBTeal,

    tertiary             = MLBBRed,
    onTertiary           = Color(0xFF1F0000),
    tertiaryContainer    = Color(0xFF390000),
    onTertiaryContainer  = MLBBRed,

    background           = SurfaceDark,
    onBackground         = TextPrimary,
    surface              = SurfaceDark,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceCard,
    onSurfaceVariant     = TextSecondary,

    error                = ErrorRed,
    onError              = Color.White,
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    outline              = SurfaceElevated,
    outlineVariant       = SurfaceElevated.copy(alpha = 0.5f)
)

/**
 * Dynamic color (Material You) is enabled on API 31+.
 * On older devices the branded dark scheme is used as the fallback.
 */
@Composable
fun MLBBAssistantTheme(
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamicColor) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        MLBBDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = MLBBTypography,
        content     = content
    )
}
