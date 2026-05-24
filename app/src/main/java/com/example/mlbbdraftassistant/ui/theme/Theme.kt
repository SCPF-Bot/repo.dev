package com.example.mlbbdraftassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Glassmorphism colour palette – low opacity, cool tones
private val GlassDarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),        // soft blue for accents
    secondary = Color(0xFFA5D6A7),      // soft green
    surface = Color(0xAA1E1E1E),        // dark semi-transparent (alpha ~0.66)
    background = Color(0xAA121212),
    onSurface = Color.White,
    onBackground = Color.White,
    error = Color(0xFFEF5350)
)

private val GlassLightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF388E3C),
    surface = Color(0xAAFFFFFF),        // light semi-transparent
    background = Color(0xAAF5F5F5),
    onSurface = Color.Black,
    onBackground = Color.Black,
    error = Color(0xFFD32F2F)
)

@Composable
fun MLBBDraftTheme(
    darkTheme: Boolean = true,          // game overlay usually dark
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) GlassDarkColors else GlassLightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}