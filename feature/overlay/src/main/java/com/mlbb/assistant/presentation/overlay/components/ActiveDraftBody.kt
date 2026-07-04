package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber

/**
 * The main body rendered while a draft is active (ban or pick phases).
 * Displays the ban phase panel, pick phase panel, and composition insights.
 */
@Composable
internal fun ActiveDraftBody(
    session:         DraftSession,
    recommendations: List<HeroScore>,
    banSuggestions:  List<BanSuggestion>,
    isBanTurn:       Boolean,
    enemyWarnings:   List<String>,
    onHeroSelected:  (Hero) -> Unit
) {
    val isBanPhase  = session.phase in listOf(DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2)
    val isPickPhase = session.phase in listOf(DraftPhase.PICK, DraftPhase.TRADING)
    val isPickTurn  = session.currentTurn?.side?.name == "OUR_TEAM"
    val pickLabel   = session.currentTurn?.let { "Pick ${it.pickNumber}/10" } ?: ""

    Column(
        modifier            = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Ban phase panel ────────────────────────────────────────────────
        PhasePanel(
            isActive    = isBanPhase,
            isDone      = isPickPhase,
            activeColor = MLBBRed,
            labelActive = "⛔  BAN PHASE",
            labelDone   = "⛔  BAN — Done"
        ) {
            BanSlotRow(
                allySlots  = buildSlotList(session.ourBansR1, session.ourBansR2),
                enemySlots = buildSlotList(session.enemyBansR1, session.enemyBansR2)
            )
            AnimatedVisibility(
                visible = isBanPhase,
                enter   = fadeIn(tween(200)) + expandVertically(),
                exit    = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TurnBadge(
                        text  = if (isBanTurn) "YOUR TURN TO BAN" else "Enemy is banning…",
                        color = if (isBanTurn) MLBBRed else TextSecondary
                    )
                    if (banSuggestions.isNotEmpty()) {
                        BanRecommendedRow(heroes = banSuggestions.take(7).map { it.hero to it.badgeLabel })
                    }
                }
            }
        }

        // ── Pick phase panel ───────────────────────────────────────────────
        PhasePanel(
            isActive    = isPickPhase,
            isDone      = false,
            activeColor = MLBBTeal,
            labelActive = "✅  PICK PHASE",
            labelDone   = "✅  PICK PHASE"
        ) {
            PickSlotRow(allySlots = session.ourPicks, enemySlots = session.enemyPicks)
            AnimatedVisibility(
                visible = isPickPhase,
                enter   = fadeIn(tween(200)) + expandVertically(),
                exit    = fadeOut(tween(150)) + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (session.phase == DraftPhase.TRADING) {
                        TurnBadge(text = "Trading phase — tap heroes to swap", color = WarningAmber)
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isPickTurn) SuccessGreen.copy(0.12f) else MLBBRed.copy(0.10f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isPickTurn) "YOUR TURN TO PICK" else "ENEMY TURN",
                                color      = if (isPickTurn) SuccessGreen else MLBBRed,
                                fontSize   = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (pickLabel.isNotEmpty()) {
                                Text(pickLabel, color = TextSecondary, fontSize = 8.sp)
                            }
                        }
                    }
                    if (recommendations.isNotEmpty()) {
                        PickRecommendedRow(
                            heroes         = recommendations.take(6).map { it.hero to it.badgeLabel },
                            onHeroSelected = onHeroSelected
                        )
                    }
                    if (enemyWarnings.isNotEmpty()) {
                        Text(
                            enemyWarnings.first(),
                            color    = WarningAmber,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        CompositionInsightsPanel(session = session)
    }
}

// ── Phase panel card ───────────────────────────────────────────────────────────

@Composable
private fun PhasePanel(
    isActive:    Boolean,
    isDone:      Boolean,
    activeColor: Color,
    labelActive: String,
    labelDone:   String,
    content:     @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue   = when { isActive -> 1.0f; isDone -> 0.55f; else -> 0.40f },
        animationSpec = tween<Float>(300),
        label         = "phase_alpha"
    )
    val borderColor by animateColorAsState(
        targetValue   = when {
            isActive -> activeColor.copy(alpha = 0.70f)
            isDone   -> activeColor.copy(alpha = 0.20f)
            else     -> TextDisabled.copy(alpha = 0.25f)
        },
        animationSpec = tween(300),
        label         = "phase_border"
    )
    val label = if (isDone) labelDone else labelActive

    Box(
        Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .then(
                if (isActive) Modifier.drawBehind {
                    drawLine(
                        color       = activeColor,
                        start       = Offset(3f, 10f),
                        end         = Offset(3f, size.height - 10f),
                        strokeWidth = 3f
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                label,
                color         = if (isActive) TextPrimary else TextSecondary,
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
            content()
        }
    }
}
