package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.CCLevel
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.advisor.CompositionArchetype
import com.mlbb.assistant.domain.advisor.MobilityLevel
import com.mlbb.assistant.domain.advisor.SustainLevel
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber

/**
 * Inline score/insight panel toggled by the 📊 button in [WidgetBottomBar].
 *
 * Displays ban phase summary, pick phase summary, composition analysis,
 * and any active warnings — all condensed to fit within the 300×200 dp widget.
 */
@Composable
internal fun WidgetScorePanel(
    session:         DraftSession,
    recommendations: List<HeroScore>,
    banSuggestions:  List<BanSuggestion>,
    enemyWarnings:   List<String>,
    onDismiss:       () -> Unit
) {
    val profile = remember(session.ourPickedHeroes) {
        CompositionAnalyzer.analyze(session.ourPickedHeroes)
    }
    val ourArchetype = remember(session.ourPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.ourPickedHeroes)
    }
    val enemyArchetype = remember(session.enemyPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.enemyPickedHeroes)
    }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── title row ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "📊  DRAFT SCORE OVERVIEW",
                color      = MLBBGold,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            ScoreDismissButton(onDismiss = onDismiss)
        }

        WidgetHRule(alpha = 0.12f)

        // ── BAN phase score ────────────────────────────────────────────────────
        ScoreSection(icon = "⛔", title = "BAN PHASE", color = MLBBRed) {
            if (banSuggestions.isEmpty()) {
                ScoreLine("No ban data yet.", TextDisabled)
            } else {
                val topBan   = banSuggestions.first()
                val banCount = (session.ourBansR1 + session.ourBansR2).count { it != null && it.id != -1 }
                ScoreLinePair(
                    label      = "Priority target",
                    value      = "${topBan.hero.name}  [${topBan.badgeLabel}]",
                    valueColor = MLBBRed
                )
                ScoreLine("Ban OP/toxic heroes first to deny enemy power picks.", TextSecondary)
                ScoreLinePair(
                    label      = "Bans placed",
                    value      = "$banCount / 5",
                    valueColor = if (banCount >= 3) SuccessGreen else WarningAmber
                )
            }
        }

        // ── PICK phase score ───────────────────────────────────────────────────
        ScoreSection(icon = "✅", title = "PICK PHASE", color = MLBBTeal) {
            if (recommendations.isEmpty() && session.ourPickedHeroes.isEmpty()) {
                ScoreLine("No pick data yet.", TextDisabled)
            } else {
                if (recommendations.isNotEmpty()) {
                    val top = recommendations.first()
                    ScoreLinePair(
                        label      = "Top suggestion",
                        value      = "${top.hero.name}  [${top.badgeLabel}]",
                        valueColor = MLBBTeal
                    )
                }
                val pickCount = session.ourPickedHeroes.count { it.id != -1 }
                val physPct   = (profile.physicalPct * 100).toInt()
                val magPct    = (profile.magicPct  * 100).toInt()
                ScoreLinePair(
                    label      = "Picks placed",
                    value      = "$pickCount / 5",
                    valueColor = if (pickCount >= 3) SuccessGreen else WarningAmber
                )
                ScoreLinePair(
                    label      = "Damage split",
                    value      = "Phys $physPct%  Magic $magPct%",
                    valueColor = if (physPct in 30..70) SuccessGreen else WarningAmber
                )
            }
        }

        // ── Composition ────────────────────────────────────────────────────────
        ScoreSection(icon = "⚖️", title = "COMPOSITION", color = MLBBGold) {
            ScoreLinePair("Your archetype",   "${ourArchetype.icon} ${ourArchetype.display}",   MLBBTeal)
            ScoreLinePair("Enemy archetype",  "${enemyArchetype.icon} ${enemyArchetype.display}", ErrorRed)
            ScoreLinePair("CC",       profile.ccLevel.name,       ccColor(profile.ccLevel))
            ScoreLinePair("Mobility", profile.mobilityLevel.name, mobilityColor(profile.mobilityLevel))
            ScoreLinePair("Sustain",  profile.sustainLevel.name,  sustainColor(profile.sustainLevel))
            if (ourArchetype != CompositionArchetype.BALANCED) {
                ScoreLine("Win condition: ${ourArchetype.winCondition.take(60)}…", TextSecondary)
            }
        }

        // ── Warnings ───────────────────────────────────────────────────────────
        val allWarnings = (profile.warnings + enemyWarnings).take(3)
        if (allWarnings.isNotEmpty()) {
            ScoreSection(icon = "⚠️", title = "WARNINGS", color = WarningAmber) {
                allWarnings.forEach { w -> ScoreLine(w, WarningAmber) }
            }
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

@Composable
private fun ScoreDismissButton(onDismiss: () -> Unit) {
    Box(
        Modifier
            .background(TextDisabled.copy(0.12f), RoundedCornerShape(4.dp))
            .clickable { onDismiss() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("✕ close", color = TextSecondary, fontSize = 7.5.sp)
    }
}

@Composable
private fun ScoreSection(
    icon:    String,
    title:   String,
    color:   Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(0.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 9.sp)
            Text(title, color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
        }
        content()
    }
}

@Composable
private fun ScoreLine(text: String, color: Color) {
    Text(text, color = color, fontSize = 7.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ScoreLinePair(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDisabled,  fontSize = 7.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

private fun ccColor(level: CCLevel) = when (level) {
    CCLevel.HIGH   -> SuccessGreen
    CCLevel.MEDIUM -> MLBBTeal
    CCLevel.LOW    -> WarningAmber
    CCLevel.NONE   -> ErrorRed
}

private fun mobilityColor(level: MobilityLevel) = when (level) {
    MobilityLevel.HIGH   -> SuccessGreen
    MobilityLevel.MEDIUM -> WarningAmber
    MobilityLevel.LOW    -> TextDisabled
}

private fun sustainColor(level: SustainLevel) = when (level) {
    SustainLevel.HIGH   -> SuccessGreen
    SustainLevel.MEDIUM -> WarningAmber
    SustainLevel.LOW    -> ErrorRed
}

/** Shared 1 dp horizontal divider used inside the overlay score panel. */
@Composable
internal fun WidgetHRule(alpha: Float = 0.15f) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MLBBGold.copy(alpha = alpha))
    )
}
