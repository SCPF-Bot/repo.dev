package com.mlbb.assistant.presentation.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.TextDisabled

// ── Template pre-population chip ──────────────────────────────────────────────

/** One-tap chip that pre-populates the mapping canvas with a standard layout. */
@Composable
internal fun TemplateChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(SurfaceDark, RoundedCornerShape(6.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .wrapContentWidth()
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.GridOn,
                contentDescription = null,
                tint     = MLBBGold,
                modifier = Modifier.size(11.dp)
            )
            Text(label, color = MLBBGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Active team toggle ─────────────────────────────────────────────────────────

/** Animated pill toggle that switches the active slot team (Ally / Enemy). */
@Composable
internal fun TeamToggle(label: String, isActive: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue   = if (isActive) color else Color.Transparent,
        animationSpec = tween(150),
        label         = "team_toggle_bg_$label"
    )
    Box(
        Modifier
            .background(bg, RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = if (isActive) SurfaceDark else color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
    }
}

// ── Slot count badge ──────────────────────────────────────────────────────────

/** Small coloured badge showing how many slots of each team are marked. */
@Composable
internal fun SlotCountBadge(label: String, color: Color, hasSlots: Boolean) {
    Row(
        Modifier
            .background(
                if (hasSlots) color.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .border(
                0.5.dp,
                if (hasSlots) color.copy(alpha = 0.35f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (hasSlots) color else TextDisabled, CircleShape)
        )
        Text(
            label,
            color      = if (hasSlots) color else TextDisabled,
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
