package com.mlbb.assistant.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MLBBTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 28.sp, color = TextPrimary),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 22.sp, color = TextPrimary),
    titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary),
    titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextSecondary),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = TextPrimary),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = TextSecondary),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, color = TextDisabled),
    labelLarge    = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 12.sp, color = TextPrimary),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, color = TextSecondary),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, color = TextDisabled)
)
