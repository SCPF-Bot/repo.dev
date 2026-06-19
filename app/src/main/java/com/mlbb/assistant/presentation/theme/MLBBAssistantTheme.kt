package com.mlbb.assistant.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Brand colours ─────────────────────────────────────────────────────────────
private val mlbbBlue    = Color(0xFF1A73E8)
private val mlbbGold    = Color(0xFFFFB300)
private val mlbbDark    = Color(0xFF0D0F14)
private val mlbbSurface = Color(0xFF1C1F26)

private val DarkColorScheme = darkColorScheme(
    primary         = mlbbBlue,
    onPrimary       = Color.White,
    primaryContainer = Color(0xFF003C8F),
    secondary       = mlbbGold,
    onSecondary     = mlbbDark,
    background      = mlbbDark,
    onBackground    = Color(0xFFE8EAF0),
    surface         = mlbbSurface,
    onSurface       = Color(0xFFE8EAF0),
    surfaceVariant  = Color(0xFF262B34),
    error           = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary         = mlbbBlue,
    onPrimary       = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    secondary       = mlbbGold,
    onSecondary     = Color(0xFF1A1A1A),
    background      = Color(0xFFF8F9FC),
    onBackground    = Color(0xFF1A1A1A),
    surface         = Color.White,
    onSurface       = Color(0xFF1A1A1A),
    error           = Color(0xFFB00020)
)

/**
 * App-level Material 3 theme.
 *
 * Uses Material You dynamic colour on API 31+ (Android 12+) with a static
 * dark/light fallback for older devices. The static dark scheme uses MLBB
 * brand colours (deep navy, gold accent) for visual consistency.
 *
 * Note: The original codebase was dark-only. The light scheme has been added
 * to satisfy the UX audit item "Dark/Light theme — light scheme not defined".
 */
@Composable
fun MLBBAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
