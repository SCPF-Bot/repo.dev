package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.advisor.CCLevel
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.advisor.CompositionArchetype
import com.mlbb.assistant.domain.advisor.MobilityLevel
import com.mlbb.assistant.domain.advisor.SustainLevel
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.OverlayBackground
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber

/**
 * Expanded mini-widget (3:2 aspect ratio, 300×200 dp, scrollable content).
 *
 * Changes from v2:
 *  ① ↺ Restart button in header — resets to IDLE.
 *  ② Recommended bans are display-only (smaller, non-clickable), show 7 heroes.
 *  ③ Bottom bar: [⏹ Min] [↩ Undo] [📊 Score] — Close removed.
 *  ④ 📊 Score toggles an inline score/insight panel inside the widget.
 *  ⑤ Header [—] and [✕] buttons are 26 dp (slightly bigger).
 *  ⑥ Widget fixed to 300×200 dp (3:2); inner content scrolls.
 */
@Composable
fun MiniWidget(
    session:          DraftSession,
    recommendations:  List<HeroScore>,
    banSuggestions:   List<BanSuggestion>,
    isBanTurn:        Boolean,
    enemyWarnings:    List<String>,
    onMinimize:       () -> Unit,
    onClose:          () -> Unit,
    onUndo:           () -> Unit,
    onScoreDetails:   () -> Unit,
    onRestartDraft:   () -> Unit,
    onHeroSelected:   (Hero) -> Unit,
    onStartDraft:     (ourTeamFirst: Boolean) -> Unit
) {
    // ── state ──────────────────────────────────────────────────────────────────
    var showScorePanel by remember { mutableStateOf(false) }

    val phaseLabel = when (session.phase) {
        DraftPhase.IDLE        -> "STANDBY"
        DraftPhase.SETUP       -> "SETUP"
        DraftPhase.BAN_ROUND_1 -> "BAN R1"
        DraftPhase.BAN_ROUND_2 -> "BAN R2"
        DraftPhase.PICK        -> "PICK"
        DraftPhase.TRADING     -> "TRADE"
        DraftPhase.COMPLETE    -> "DONE"
    }

    val isDraftActive = session.phase !in listOf(DraftPhase.IDLE, DraftPhase.SETUP)

    // ── root — fixed 3:2 (300×200 dp) ─────────────────────────────────────────
    Box(
        modifier = Modifier
            .width(300.dp)
            .height(200.dp)
            .background(OverlayBackground, RoundedCornerShape(14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
    ) {
        Column {
            // ── header (fixed, not scrollable) ─────────────────────────────────
            WidgetHeader(
                phaseLabel      = phaseLabel,
                isDraftActive   = isDraftActive,
                onMinimize      = onMinimize,
                onClose         = onClose,
                onRestartDraft  = onRestartDraft
            )

            HRule(alpha = 0.15f)

            // ── scrollable body ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (session.phase) {
                    DraftPhase.IDLE, DraftPhase.SETUP -> {
                        IdleBody(session = session, onStartDraft = onStartDraft)
                    }
                    DraftPhase.COMPLETE -> {
                        CompleteBody(onClose = onClose)
                    }
                    else -> {
                        if (showScorePanel) {
                            ScorePanel(
                                session        = session,
                                recommendations = recommendations,
                                banSuggestions  = banSuggestions,
                                enemyWarnings   = enemyWarnings,
                                onDismiss       = { showScorePanel = false }
                            )
                        } else {
                            ActiveDraftBody(
                                session         = session,
                                recommendations = recommendations,
                                banSuggestions  = banSuggestions,
                                isBanTurn       = isBanTurn,
                                enemyWarnings   = enemyWarnings,
                                onHeroSelected  = onHeroSelected
                            )
                        }
                    }
                }
            }

            // ── bottom bar (fixed, not scrollable) ──────────────────────────────
            if (isDraftActive) {
                HRule(alpha = 0.12f)
                BottomActionBar(
                    canUndo       = session.undoStack.isNotEmpty(),
                    scoreActive   = showScorePanel,
                    onMinimize    = onMinimize,
                    onUndo        = onUndo,
                    onScore       = { showScorePanel = !showScorePanel }
                )
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun WidgetHeader(
    phaseLabel:     String,
    isDraftActive:  Boolean,
    onMinimize:     () -> Unit,
    onClose:        () -> Unit,
    onRestartDraft: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Left: drag handle + title + phase
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DragHandle()
            Text(
                "MLBB",
                color      = MLBBGold,
                fontWeight = FontWeight.Bold,
                fontSize   = 10.sp
            )
            Text(
                "DRAFT",
                color      = MLBBGold.copy(alpha = 0.6f),
                fontSize   = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (phaseLabel.isNotEmpty() && phaseLabel != "STANDBY") {
                Text(
                    "· $phaseLabel",
                    color    = TextSecondary,
                    fontSize = 8.sp
                )
            }
        }

        // Right: restart (when active) + minimize + close
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (isDraftActive) {
                IconBtn(
                    label   = "↺",
                    color   = WarningAmber,
                    tooltip = "Restart",
                    onClick = onRestartDraft
                )
            }
            IconBtn(label = "—", color = TextSecondary, onClick = onMinimize)
            IconBtn(label = "✕", color = ErrorRed,      onClick = onClose)
        }
    }
}

@Composable
private fun DragHandle() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(2.5.dp)
                            .clip(CircleShape)
                            .background(TextDisabled)
                    )
                }
            }
        }
    }
}

/** Slightly bigger header icon button — 26×26 dp */
@Composable
private fun IconBtn(
    label:   String,
    color:   Color,
    tooltip: String = "",
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(26.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Active draft body ──────────────────────────────────────────────────────────

@Composable
private fun ActiveDraftBody(
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

    val enemyArchetype = remember(session.enemyPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.enemyPickedHeroes)
    }
    val ourArchetype = remember(session.ourPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.ourPickedHeroes)
    }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        // ══ BAN PHASE PANEL ════════════════════════════════════════════════════
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
                        // Display-only (not clickable), 7 heroes, smaller chips
                        BanRecommendedRow(
                            heroes = banSuggestions.take(7).map { it.hero to it.badgeLabel }
                        )
                    }
                }
            }
        }

        // ══ PICK PHASE PANEL ═══════════════════════════════════════════════════
        PhasePanel(
            isActive    = isPickPhase,
            isDone      = false,
            activeColor = MLBBTeal,
            labelActive = "✅  PICK PHASE",
            labelDone   = "✅  PICK PHASE"
        ) {
            PickSlotRow(
                allySlots  = session.ourPicks,
                enemySlots = session.enemyPicks
            )

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
                                    if (isPickTurn) SuccessGreen.copy(0.12f) else ErrorRed.copy(0.10f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isPickTurn) "YOUR TURN TO PICK" else "ENEMY TURN",
                                color      = if (isPickTurn) SuccessGreen else ErrorRed,
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

        // ══ COMPOSITION INSIGHTS ═══════════════════════════════════════════════
        CompositionInsightsPanel(
            enemyArchetype = enemyArchetype,
            ourArchetype   = ourArchetype,
            session        = session
        )
    }
}

// ── Score panel (toggled by 📊 button) ────────────────────────────────────────

@Composable
private fun ScorePanel(
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
            Box(
                Modifier
                    .background(TextDisabled.copy(0.12f), RoundedCornerShape(4.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("✕ close", color = TextSecondary, fontSize = 7.5.sp)
            }
        }

        HRule(alpha = 0.12f)

        // ── BAN phase score ────────────────────────────────────────────────────
        ScoreSection(
            icon  = "⛔",
            title = "BAN PHASE",
            color = MLBBRed
        ) {
            if (banSuggestions.isEmpty()) {
                ScoreLine("No ban data yet.", TextDisabled)
            } else {
                val topBan = banSuggestions.first()
                ScoreLinePair(
                    label = "Priority target",
                    value = "${topBan.hero.name}  [${topBan.badgeLabel}]",
                    valueColor = MLBBRed
                )
                ScoreLine(
                    "Ban OP/toxic heroes first to deny enemy power picks.",
                    TextSecondary
                )
                val banCount = buildSlotList(
                    session.ourBansR1, session.ourBansR2
                ).count { it != null && it.id != -1 }
                ScoreLinePair(
                    label = "Bans placed",
                    value = "$banCount / 5",
                    valueColor = if (banCount >= 3) SuccessGreen else WarningAmber
                )
            }
        }

        // ── PICK phase score ───────────────────────────────────────────────────
        ScoreSection(
            icon  = "✅",
            title = "PICK PHASE",
            color = MLBBTeal
        ) {
            if (recommendations.isEmpty() && session.ourPickedHeroes.isEmpty()) {
                ScoreLine("No pick data yet.", TextDisabled)
            } else {
                if (recommendations.isNotEmpty()) {
                    val top = recommendations.first()
                    ScoreLinePair(
                        label = "Top suggestion",
                        value = "${top.hero.name}  [${top.badgeLabel}]",
                        valueColor = MLBBTeal
                    )
                }
                val pickCount = session.ourPickedHeroes.count { it.id != -1 }
                ScoreLinePair(
                    label = "Picks placed",
                    value = "$pickCount / 5",
                    valueColor = if (pickCount >= 3) SuccessGreen else WarningAmber
                )
                // Composition colour balance
                val physPct = (profile.physicalPct * 100).toInt()
                val magPct  = (profile.magicPct  * 100).toInt()
                ScoreLinePair(
                    label = "Damage split",
                    value = "Phys $physPct%  Magic $magPct%",
                    valueColor = if (physPct in 30..70) SuccessGreen else WarningAmber
                )
            }
        }

        // ── Composition ────────────────────────────────────────────────────────
        ScoreSection(
            icon  = "⚖️",
            title = "COMPOSITION",
            color = MLBBGold
        ) {
            ScoreLinePair(
                label = "Your archetype",
                value = "${ourArchetype.icon} ${ourArchetype.display}",
                valueColor = MLBBTeal
            )
            ScoreLinePair(
                label = "Enemy archetype",
                value = "${enemyArchetype.icon} ${enemyArchetype.display}",
                valueColor = ErrorRed
            )
            ScoreLinePair(
                label = "CC",
                value = profile.ccLevel.name,
                valueColor = ccColor(profile.ccLevel)
            )
            ScoreLinePair(
                label = "Mobility",
                value = profile.mobilityLevel.name,
                valueColor = mobilityColor(profile.mobilityLevel)
            )
            ScoreLinePair(
                label = "Sustain",
                value = profile.sustainLevel.name,
                valueColor = sustainColor(profile.sustainLevel)
            )
            if (ourArchetype != CompositionArchetype.BALANCED) {
                ScoreLine(
                    "Win condition: ${ourArchetype.winCondition.take(60)}…",
                    TextSecondary
                )
            }
        }

        // ── Warnings ───────────────────────────────────────────────────────────
        val allWarnings = (profile.warnings + enemyWarnings).take(3)
        if (allWarnings.isNotEmpty()) {
            ScoreSection(icon = "⚠️", title = "WARNINGS", color = WarningAmber) {
                allWarnings.forEach { w ->
                    ScoreLine(w, WarningAmber)
                }
            }
        }
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextDisabled, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color      = valueColor,
            fontSize   = 7.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.End
        )
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
        targetValue = when {
            isActive -> 1.0f
            isDone   -> 0.55f
            else     -> 0.40f
        },
        animationSpec = tween<Float>(300),
        label = "phase_alpha"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor.copy(alpha = 0.70f)
            isDone   -> activeColor.copy(alpha = 0.20f)
            else     -> TextDisabled.copy(alpha = 0.25f)
        },
        animationSpec = tween(300),
        label = "phase_border"
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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

// ── Slot rows ─────────────────────────────────────────────────────────────────

@Composable
private fun BanSlotRow(allySlots: List<Hero?>, enemySlots: List<Hero?>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        SlotGroup(label = "Ally", slots = allySlots, filledColor = MLBBTeal)
        Box(Modifier.width(1.dp).height(16.dp).background(TextDisabled.copy(alpha = 0.3f)))
        SlotGroup(label = "Enemy", slots = enemySlots, filledColor = MLBBRed)
    }
}

@Composable
private fun PickSlotRow(allySlots: List<Hero?>, enemySlots: List<Hero?>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        SlotGroup(label = "Ally", slots = allySlots, filledColor = MLBBTeal)
        Box(Modifier.width(1.dp).height(16.dp).background(TextDisabled.copy(alpha = 0.3f)))
        SlotGroup(label = "Enemy", slots = enemySlots, filledColor = MLBBRed)
    }
}

@Composable
private fun SlotGroup(label: String, slots: List<Hero?>, filledColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = TextDisabled, fontSize = 6.5.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            slots.take(5).forEach { hero -> SlotDot(hero = hero, filledColor = filledColor) }
            repeat((5 - slots.size).coerceAtLeast(0)) { SlotDot(hero = null, filledColor = filledColor) }
        }
    }
}

@Composable
private fun SlotDot(hero: Hero?, filledColor: Color) {
    val isFilled = hero != null && hero.id != -1
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (isFilled) filledColor.copy(alpha = 0.25f) else SurfaceElevated)
            .border(
                width = if (isFilled) 1.dp else 0.5.dp,
                color = if (isFilled) filledColor.copy(alpha = 0.80f) else TextDisabled.copy(0.35f),
                shape = RoundedCornerShape(2.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isFilled && hero != null) {
            Text(
                hero.name.take(1),
                color      = filledColor,
                fontSize   = 5.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Ban recommendations (display-only, smaller, 7 heroes rows 4+3) ─────────────

@Composable
private fun BanRecommendedRow(heroes: List<Pair<Hero, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "RECOMMENDED BANS",
            color         = MLBBRed.copy(alpha = 0.8f),
            fontSize      = 7.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
        val row1 = heroes.take(4)
        val row2 = heroes.drop(4).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            row1.forEach { (hero, badge) -> BanHeroChip(hero, badge) }
        }
        if (row2.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row2.forEach { (hero, badge) -> BanHeroChip(hero, badge) }
            }
        }
    }
}

/** Smaller, display-only chip for bans — no click, no portrait, just name + badge */
@Composable
private fun BanHeroChip(hero: Hero, badge: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MLBBRed.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
            .border(0.5.dp, MLBBRed.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .width(44.dp)
    ) {
        Text(
            hero.name,
            color     = TextPrimary,
            fontSize  = 6.5.sp,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            badge,
            color      = MLBBRed,
            fontSize   = 6.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
    }
}

// ── Pick recommendations (clickable, with portrait) ────────────────────────────

@Composable
private fun PickRecommendedRow(
    heroes:         List<Pair<Hero, String>>,
    onHeroSelected: (Hero) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "RECOMMENDED PICKS",
            color         = MLBBGold,
            fontSize      = 7.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
        val row1 = heroes.take(3)
        val row2 = heroes.drop(3).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row1.forEach { (hero, badge) -> QuickHeroChip(hero, badge, onHeroSelected) }
        }
        if (row2.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row2.forEach { (hero, badge) -> QuickHeroChip(hero, badge, onHeroSelected) }
            }
        }
    }
}

@Composable
private fun QuickHeroChip(hero: Hero, badge: String, onTap: (Hero) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(SurfaceMid, RoundedCornerShape(7.dp))
            .clickable { onTap(hero) }
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .width(52.dp)
    ) {
        HeroPortrait(hero = hero, size = 36.dp)
        Spacer(Modifier.height(1.dp))
        Text(
            hero.name,
            color     = TextPrimary,
            fontSize  = 6.5.sp,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            badge,
            color      = MLBBGold,
            fontSize   = 6.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
    }
}

// ── Composition insights ───────────────────────────────────────────────────────

@Composable
private fun CompositionInsightsPanel(
    enemyArchetype: CompositionArchetype,
    ourArchetype:   CompositionArchetype,
    session:        DraftSession
) {
    val hasPicks = session.ourPickedHeroes.isNotEmpty() || session.enemyPickedHeroes.isNotEmpty()
    if (!hasPicks) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(MLBBGold.copy(alpha = 0.18f)))
            Text(
                "COMP INSIGHTS",
                color         = MLBBGold,
                fontSize      = 7.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            Box(Modifier.weight(1f).height(1.dp).background(MLBBGold.copy(alpha = 0.18f)))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ArchetypeChip(prefix = "Enemy",   archetype = enemyArchetype, chipColor = ErrorRed)
            ArchetypeChip(prefix = "Ours",    archetype = ourArchetype,   chipColor = MLBBTeal)
        }

        if (session.ourPickedHeroes.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Win Condition", color = MLBBGold, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        ourArchetype.winCondition,
                        color    = TextSecondary,
                        fontSize = 7.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchetypeChip(prefix: String, archetype: CompositionArchetype, chipColor: Color) {
    Row(
        Modifier
            .background(chipColor.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
            .border(0.5.dp, chipColor.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(archetype.icon, fontSize = 10.sp)
        Column {
            Text(prefix, color = TextDisabled, fontSize = 6.sp)
            Text(archetype.display, color = chipColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Bottom action bar (no Close button) ───────────────────────────────────────

@Composable
private fun BottomActionBar(
    canUndo:     Boolean,
    scoreActive: Boolean,
    onMinimize:  () -> Unit,
    onUndo:      () -> Unit,
    onScore:     () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ActionBarBtn(
            label    = "⏹ Min",
            color    = TextSecondary,
            modifier = Modifier.weight(1f),
            onClick  = onMinimize
        )
        ActionBarBtn(
            label    = "↩ Undo",
            color    = if (canUndo) WarningAmber else TextDisabled,
            modifier = Modifier.weight(1f),
            onClick  = { if (canUndo) onUndo() }
        )
        ActionBarBtn(
            label    = if (scoreActive) "📊 Hide" else "📊 Score",
            color    = if (scoreActive) MLBBGold else MLBBTeal,
            modifier = Modifier.weight(1f),
            onClick  = onScore
        )
    }
}

@Composable
private fun ActionBarBtn(
    label:    String,
    color:    Color,
    modifier: Modifier,
    onClick:  () -> Unit
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = color,
            fontSize   = 8.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────────

@Composable
private fun TurnBadge(text: String, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
private fun HRule(alpha: Float = 0.15f) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MLBBGold.copy(alpha = alpha))
    )
}

// ── IDLE body ─────────────────────────────────────────────────────────────────

@Composable
private fun IdleBody(session: DraftSession, onStartDraft: (Boolean) -> Unit) {
    var ourTeamFirst by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for draft to begin…", color = TextSecondary, fontSize = 9.sp)
        }

        Text(
            "WHO PICKS FIRST?",
            color      = MLBBGold,
            fontSize   = 8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TeamToggleBtn(
                emoji         = "🔵",
                label         = "ALLY",
                isSelected    = ourTeamFirst,
                selectedColor = MLBBTeal,
                modifier      = Modifier.weight(1f),
                onClick       = { ourTeamFirst = true }
            )
            TeamToggleBtn(
                emoji         = "🔴",
                label         = "ENEMY",
                isSelected    = !ourTeamFirst,
                selectedColor = ErrorRed,
                modifier      = Modifier.weight(1f),
                onClick       = { ourTeamFirst = false }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MLBBGold.copy(alpha = 0.15f), RoundedCornerShape(7.dp))
                .border(1.5.dp, MLBBGold.copy(alpha = 0.70f), RoundedCornerShape(7.dp))
                .clickable { onStartDraft(ourTeamFirst) }
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶  START DRAFT",
                color      = MLBBGold,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TeamToggleBtn(
    emoji:         String,
    label:         String,
    isSelected:    Boolean,
    selectedColor: Color,
    modifier:      Modifier,
    onClick:       () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) selectedColor.copy(alpha = 0.22f) else SurfaceMid,
                RoundedCornerShape(7.dp)
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) selectedColor else TextDisabled.copy(alpha = 0.4f),
                shape = RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, fontSize = 13.sp)
            Text(
                label,
                color      = if (isSelected) selectedColor else TextSecondary,
                fontSize   = 8.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center
            )
        }
    }
}

// ── COMPLETE body ─────────────────────────────────────────────────────────────

@Composable
private fun CompleteBody(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Draft complete ✅",
                color      = SuccessGreen,
                fontWeight = FontWeight.Bold,
                fontSize   = 12.sp
            )
            Box(
                Modifier
                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.40f), RoundedCornerShape(5.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    "Close overlay",
                    color      = SuccessGreen,
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Slot list builder ──────────────────────────────────────────────────────────

private fun buildSlotList(r1: List<Hero?>, r2: List<Hero?>): List<Hero?> =
    if (r2.isEmpty()) r1 else r1 + r2
