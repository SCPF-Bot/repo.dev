package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun FloatingBubble(
    session: DraftSession,
    isExpanded: Boolean,
    onTap: () -> Unit
) {
    val isDraftActive = session.phase !in listOf(DraftPhase.IDLE, DraftPhase.COMPLETE)

    // Pulse animation when draft is active
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1.0f, targetValue = if (isDraftActive) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val glowAlpha by pulse.animateFloat(
        initialValue = 0.4f, targetValue = if (isDraftActive) 0.9f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    val phaseLabel = when (session.phase) {
        DraftPhase.SETUP       -> "SETUP"
        DraftPhase.BAN_ROUND_1 -> "BAN"
        DraftPhase.BAN_ROUND_2 -> "BAN R2"
        DraftPhase.PICK        -> "PICK"
        DraftPhase.TRADING     -> "TRADE"
        DraftPhase.COMPLETE    -> "DONE"
        DraftPhase.IDLE        -> ""
    }

    Box(contentAlignment = Alignment.Center) {
        // Glow ring
        if (isDraftActive) {
            Box(
                Modifier
                    .size(66.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(MLBBGold.copy(alpha = glowAlpha * 0.3f), MLBBGold.copy(alpha = 0f))
                        )
                    )
                    .border(2.dp, MLBBGold.copy(alpha = glowAlpha), CircleShape)
            )
        }

        // Bubble
        Box(
            Modifier
                .size(56.dp)
                .scale(if (isDraftActive) scale else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(SurfaceElevated, SurfaceDark))
                )
                .border(2.dp, if (isDraftActive) MLBBGold else SurfaceMid, CircleShape)
                .clickable { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isExpanded) "▼" else "⚔️", fontSize = 20.sp)
                if (phaseLabel.isNotEmpty()) {
                    Text(phaseLabel, color = MLBBGold, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
