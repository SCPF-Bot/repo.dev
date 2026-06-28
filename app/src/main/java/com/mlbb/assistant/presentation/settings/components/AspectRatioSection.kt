package com.mlbb.assistant.presentation.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.capture.AspectRatioPreset
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary

/**
 * Aspect-ratio picker for the Settings screen.
 *
 * Renders a row of three pill buttons — one per [AspectRatioPreset] — followed
 * by a one-line description of the currently selected preset written in plain,
 * non-technical language for players who aren't familiar with aspect ratios.
 *
 * Layout:
 * ```
 * ┌────────┬───────────┬────────────┐
 * │  Auto  │  Standard │ Widescreen │   ← three pill buttons, full-width
 * └────────┴───────────┴────────────┘
 *   "The app detects your screen …"      ← description of the selected choice
 * ```
 *
 * The active pill is highlighted with the app's gold accent.
 * Inactive pills are dim so the selection is immediately obvious.
 */
@Composable
internal fun AspectRatioSection(
    selected:   AspectRatioPreset,
    onSelected: (AspectRatioPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text  = "Screen shape",
            color = TextSecondary,
            fontSize = 12.sp
        )

        // ── Pill button row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AspectRatioPreset.entries.forEach { preset ->
                AspectRatioPill(
                    preset     = preset,
                    isSelected = preset == selected,
                    onClick    = { onSelected(preset) },
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        // ── Description for selected preset ───────────────────────────────────
        Text(
            text     = selected.description,
            color    = TextDisabled,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AspectRatioPill(
    preset:     AspectRatioPreset,
    isSelected: Boolean,
    onClick:    () -> Unit,
    modifier:   Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) MLBBGold.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(200),
        label         = "pill_bg_${preset.key}"
    )
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) MLBBGold else SurfaceElevated,
        animationSpec = tween(200),
        label         = "pill_border_${preset.key}"
    )
    val labelColor by animateColorAsState(
        targetValue   = if (isSelected) MLBBGold else TextPrimary,
        animationSpec = tween(200),
        label         = "pill_label_${preset.key}"
    )
    val subColor by animateColorAsState(
        targetValue   = if (isSelected) MLBBGold.copy(alpha = 0.70f) else TextDisabled,
        animationSpec = tween(200),
        label         = "pill_sub_${preset.key}"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .semantics {
                contentDescription = buildString {
                    append(preset.friendlyName)
                    if (isSelected) append(", selected")
                    else append(", tap to select")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = preset.label,
                color      = labelColor,
                fontSize   = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center
            )
            val subtitle = when (preset) {
                AspectRatioPreset.AUTO            -> "recommended"
                AspectRatioPreset.STANDARD_16_9   -> "16 : 9"
                AspectRatioPreset.ULTRAWIDE_21_9  -> "21 : 9"
            }
            Text(
                text      = subtitle,
                color     = subColor,
                fontSize  = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
