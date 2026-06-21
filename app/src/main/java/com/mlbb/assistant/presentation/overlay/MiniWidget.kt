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
import androidx.compose.foundation.layout.widthIn
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
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.advisor.CompositionArchetype
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
 * Expanded mini-widget shown when the user taps the floating bubble.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  ⠿ MLBB DRAFT ASSISTANT        [—]  [✕]         │  drag-handle header
 * ├══════════════════════════════════════════════════╡
 * │  ╔══ BAN PHASE ══════════════════════════════╗   │  ← ACTIVE = bright border
 * │  ║  Ally:  □ □ □ □ □  │  Enemy: □ □ □ □ □   ║   │    DONE  = dimmed
 * │  ║  Recommended Bans:  [Hero] [Hero] [Hero]  ║   │
 * │  ╚═══════════════════════════════════════════╝   │
 * │  ╔══ PICK PHASE ═════════════════════════════╗   │  ← inactive until ban done
 * │  ║  Ally:  □ □ □ □ □  │  Enemy: □ □ □ □ □   ║   │
 * │  ║  Recommended Picks: [Hero] [Hero] [Hero]  ║   │
 * │  ╚═══════════════════════════════════════════╝   │
 * │  ── COMPOSITION INSIGHTS ──────────────────────  │
 * │  Enemy: ⚡ Dive  │  Ours: ⚖ Balanced            │
 * │  Win: Force teamfights and chain CC.              │
 * ├──────────────────────────────────────────────────┤
 * │  [⏹ Min]   [🔄 Undo]   [📊 Score]   [✕ Close]  │
 * └──────────────────────────────────────────────────┘
 *
 * Sequential phase logic:
 *  - BAN_ROUND_1 / BAN_ROUND_2 → BAN panel active, PICK panel dimmed
 *  - PICK / TRADING             → BAN panel dimmed (done), PICK panel active
 *  Both panels are always visible; only their visual weight changes.
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
    onHeroSelected:   (Hero) -> Unit,
    onStartDraft:     (ourTeamFirst: Boolean) -> Unit
) {
    val phaseLabel = when (session.phase) {
        DraftPhase.IDLE        -> "STANDBY"
        DraftPhase.SETUP       -> "SETUP"
        DraftPhase.BAN_ROUND_1 -> "BAN R1"
        DraftPhase.BAN_ROUND_2 -> "BAN R2"
        DraftPhase.PICK        -> "PICK"
        DraftPhase.TRADING     -> "TRADE"
        DraftPhase.COMPLETE    -> "DONE"
    }

    Box(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .background(OverlayBackground, RoundedCornerShape(14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Column {
            WidgetHeader(
                phaseLabel     = phaseLabel,
                onMinimize     = onMinimize,
                onClose        = onClose
            )

            HRule(alpha = 0.15f)

            when (session.phase) {
                DraftPhase.IDLE, DraftPhase.SETUP -> {
                    IdleBody(session = session, onStartDraft = onStartDraft)
                }
                DraftPhase.COMPLETE -> {
                    CompleteBody(onClose = onClose)
                }
                else -> {
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

            // Bottom action bar — shown whenever draft is active or complete
            if (session.phase != DraftPhase.IDLE && session.phase != DraftPhase.SETUP) {
                HRule(alpha = 0.12f)
                BottomActionBar(
                    canUndo        = session.undoStack.isNotEmpty(),
                    onMinimize     = onMinimize,
                    onUndo         = onUndo,
                    onScoreDetails = onScoreDetails,
                    onClose        = onClose
                )
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun WidgetHeader(phaseLabel: String, onMinimize: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DragHandle()
            Text(
                "MLBB DRAFT",
                color      = MLBBGold,
                fontWeight = FontWeight.Bold,
                fontSize   = 11.sp
            )
            Text(
                "ASSISTANT",
                color      = MLBBGold.copy(alpha = 0.55f),
                fontSize   = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (phaseLabel.isNotEmpty() && phaseLabel != "STANDBY") {
                Text(
                    "· $phaseLabel",
                    color    = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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

@Composable
private fun IconBtn(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(20.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

    // Composition insights (computed from picks so far)
    val enemyArchetype = remember(session.enemyPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.enemyPickedHeroes)
    }
    val ourArchetype = remember(session.ourPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.ourPickedHeroes)
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // ══ BAN PHASE PANEL ════════════════════════════════════════════════════
        PhasePanel(
            isActive      = isBanPhase,
            isDone        = isPickPhase,
            activeColor   = MLBBRed,
            labelActive   = "⛔  BAN PHASE",
            labelDone     = "⛔  BAN PHASE — Done"
        ) {
            // Slot row: Ally bans | Enemy bans
            BanSlotRow(
                allySlots  = buildSlotList(session.ourBansR1, session.ourBansR2),
                enemySlots = buildSlotList(session.enemyBansR1, session.enemyBansR2)
            )

            // Turn indicator (only when this phase is active)
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
                        RecommendedRow(
                            label          = "RECOMMENDED BANS",
                            heroes         = banSuggestions.take(6).map { it.hero to it.badgeLabel },
                            onHeroSelected = onHeroSelected
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
            // Slot row: Ally picks | Enemy picks
            PickSlotRow(
                allySlots  = session.ourPicks,
                enemySlots = session.enemyPicks
            )

            // Turn indicator (only when pick phase is active)
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
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isPickTurn) "YOUR TURN TO PICK" else "ENEMY TURN",
                                color      = if (isPickTurn) SuccessGreen else ErrorRed,
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (pickLabel.isNotEmpty()) {
                                Text(pickLabel, color = TextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                    if (recommendations.isNotEmpty()) {
                        RecommendedRow(
                            label          = "RECOMMENDED PICKS",
                            heroes         = recommendations.take(6).map { it.hero to it.badgeLabel },
                            onHeroSelected = onHeroSelected
                        )
                    }
                    if (enemyWarnings.isNotEmpty()) {
                        Text(
                            enemyWarnings.first(),
                            color    = WarningAmber,
                            fontSize = 9.sp,
                            maxLines = 2,
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

// ── Phase panel card ───────────────────────────────────────────────────────────

/**
 * A bordered card whose visual weight reflects whether this phase is currently
 * active, done, or pending.
 *
 * Active → full opacity, colored left accent bar + border
 * Done   → 55 % opacity, muted border, label suffixed with "— Done"
 * Pending → 40 % opacity, no accent, dim border
 */
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
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(
                if (isActive) Modifier.drawBehind {
                    drawLine(
                        color       = activeColor,
                        start       = Offset(3f, 12f),
                        end         = Offset(3f, size.height - 12f),
                        strokeWidth = 3.5f
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Section title
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    color      = if (isActive) TextPrimary else TextSecondary,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
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
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(TextDisabled.copy(alpha = 0.3f))
        )
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
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(TextDisabled.copy(alpha = 0.3f))
        )
        SlotGroup(label = "Enemy", slots = enemySlots, filledColor = MLBBRed)
    }
}

@Composable
private fun SlotGroup(label: String, slots: List<Hero?>, filledColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = TextDisabled, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            slots.take(5).forEach { hero ->
                SlotDot(hero = hero, filledColor = filledColor)
            }
            // pad to 5 if fewer slots
            repeat((5 - slots.size).coerceAtLeast(0)) {
                SlotDot(hero = null, filledColor = filledColor)
            }
        }
    }
}

@Composable
private fun SlotDot(hero: Hero?, filledColor: Color) {
    val isFilled = hero != null && hero.id != -1
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isFilled) filledColor.copy(alpha = 0.25f) else SurfaceElevated)
            .border(
                width = if (isFilled) 1.dp else 0.5.dp,
                color = if (isFilled) filledColor.copy(alpha = 0.80f) else TextDisabled.copy(0.35f),
                shape = RoundedCornerShape(3.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isFilled && hero != null) {
            Text(
                text     = hero.name.take(1),
                color    = filledColor,
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Recommended heroes row ─────────────────────────────────────────────────────

@Composable
private fun RecommendedRow(
    label:          String,
    heroes:         List<Pair<Hero, String>>,
    onHeroSelected: (Hero) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color    = MLBBGold,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
        // Two rows of up to 3 chips each
        val row1 = heroes.take(3)
        val row2 = heroes.drop(3).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            row1.forEach { (hero, badge) -> QuickHeroChip(hero, badge, onHeroSelected) }
        }
        if (row2.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
            .background(SurfaceMid, RoundedCornerShape(8.dp))
            .clickable { onTap(hero) }
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .width(56.dp)
    ) {
        HeroPortrait(hero = hero, size = 40.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            hero.name,
            color    = TextPrimary,
            fontSize = 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            badge,
            color      = MLBBGold,
            fontSize   = 6.5.sp,
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
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Section heading
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(MLBBGold.copy(alpha = 0.20f)))
            Text(
                "COMPOSITION INSIGHTS",
                color      = MLBBGold,
                fontSize   = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Box(Modifier.weight(1f).height(1.dp).background(MLBBGold.copy(alpha = 0.20f)))
        }

        // Archetype row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ArchetypeChip(
                prefix    = "Enemy",
                archetype = enemyArchetype,
                chipColor = ErrorRed
            )
            ArchetypeChip(
                prefix    = "Your Team",
                archetype = ourArchetype,
                chipColor = MLBBTeal
            )
        }

        // Win condition for our team
        if (session.ourPickedHeroes.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Win Condition",
                        color      = MLBBGold,
                        fontSize   = 7.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        ourArchetype.winCondition,
                        color    = TextSecondary,
                        fontSize = 8.sp,
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
            .background(chipColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .border(0.5.dp, chipColor.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(archetype.icon, fontSize = 11.sp)
        Column {
            Text(
                prefix,
                color    = TextDisabled,
                fontSize = 7.sp
            )
            Text(
                archetype.display,
                color      = chipColor,
                fontSize   = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Bottom action bar ──────────────────────────────────────────────────────────

@Composable
private fun BottomActionBar(
    canUndo:        Boolean,
    onMinimize:     () -> Unit,
    onUndo:         () -> Unit,
    onScoreDetails: () -> Unit,
    onClose:        () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
            label    = "📊 Score",
            color    = MLBBTeal,
            modifier = Modifier.weight(1f),
            onClick  = onScoreDetails
        )
        ActionBarBtn(
            label    = "✕ Close",
            color    = ErrorRed,
            modifier = Modifier.weight(1f),
            onClick  = onClose
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
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = color,
            fontSize   = 8.5.sp,
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
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for draft to begin…", color = TextSecondary, fontSize = 10.sp)
        }

        Text(
            "WHO PICKS FIRST?",
            color      = MLBBGold,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                .background(MLBBGold.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.5.dp, MLBBGold.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                .clickable { onStartDraft(ourTeamFirst) }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶  START DRAFT",
                color      = MLBBGold,
                fontSize   = 11.sp,
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
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) selectedColor else TextDisabled.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, fontSize = 14.sp)
            Text(
                label,
                color      = if (isSelected) selectedColor else TextSecondary,
                fontSize   = 9.sp,
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
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Draft complete ✅",
                color      = SuccessGreen,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp
            )
            Box(
                Modifier
                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "Close overlay",
                    color      = SuccessGreen,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Slot list builder ──────────────────────────────────────────────────────────

private fun buildSlotList(r1: List<Hero?>, r2: List<Hero?>): List<Hero?> =
    if (r2.isEmpty()) r1 else r1 + r2
