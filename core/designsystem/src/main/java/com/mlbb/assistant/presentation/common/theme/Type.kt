package com.mlbb.assistant.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colours are intentionally OMITTED from every TextStyle.
// M3 Typography must not embed colours — colour comes from the ColorScheme so that
// dynamic colour (Material You) and theming work correctly across all devices.
val MLBBTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 28.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 22.sp, letterSpacing = (-0.25).sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 13.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, letterSpacing = 0.25.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, letterSpacing = 0.2.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 11.sp, letterSpacing = 0.15.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 10.sp, letterSpacing = 0.5.sp)
)
