package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.BanSuggestion
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
 * Layout:
 *  ┌─────────────────────────────────────────────────┐
 *  │  MLBB DRAFT · PHASE           [—]  [✕]         │  ← drag-handle header
 *  ├─────────────────────────────────────────────────┤
 *  │  [IDLE]  team picker  +  START DRAFT            │
 *  │  [ACTIVE] combined ban + pick panel (scrollable)│
 *  │    ─ BAN turn indicator + top-3 suggestions     │
 *  │    ─ PICK turn indicator + top-3 suggestions    │
 *  │    ─ slot-fill overview                         │
 *  │  [DONE]  close button                           │
 *  └─────────────────────────────────────────────────┘
 *
 * Key change: the BAN phase body and the PICK phase body are shown together in
 * a single scrollable column whenever the draft is active (any non-IDLE,
 * non-COMPLETE phase).  This means the user always sees both ban recommendations
 * and pick recommendations at the same time with a single tap of the bubble,
 * regardless of which phase the engine currently reports.
 */
@Composable
fun MiniWidget(
    session:         DraftSession,
    recommendations: List<HeroScore>,
    banSuggestions:  List<BanSuggestion>,
    isBanTurn:       Boolean,
    enemyWarnings:   List<String>,
    onMinimize:      () -> Unit,
    onClose:         () -> Unit,
    onHeroSelected:  (Hero) -> Unit,
    onStartDraft:    (ourTeamFirst: Boolean) -> Unit
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
            .widthIn(min = 240.dp, max = 300.dp)
            .background(OverlayBackground, RoundedCornerShape(14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
    ) {
        Column {
            // ── Header ─────────────────────────────────────────────────────────
            WidgetHeader(
                phaseLabel = phaseLabel,
                onMinimize = onMinimize,
                onClose    = onClose
            )

            // ── Divider ────────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MLBBGold.copy(alpha = 0.15f))
            )

            // ── Body ───────────────────────────────────────────────────────────
            when (session.phase) {
                DraftPhase.IDLE, DraftPhase.SETUP -> {
                    IdleBody(
                        session      = session,
                        onStartDraft = onStartDraft
                    )
                }

                DraftPhase.COMPLETE -> {
                    CompleteBody(onClose = onClose)
                }

                else -> {
                    // ACTIVE DRAFT — show ban panel + pick panel together
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
            // Drag-handle dots (visual cue only — actual drag is View-level)
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
            Text(
                "MLBB DRAFT",
                color      = MLBBGold,
                fontWeight = FontWeight.Bold,
                fontSize   = 11.sp
            )
            Text(
                phaseLabel,
                color      = TextSecondary,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBtn(label = "—", color = TextSecondary, onClick = onMinimize)
            IconBtn(label = "✕", color = ErrorRed,      onClick = onClose)
        }
    }
}

@Composable
private fun IconBtn(
    label:   String,
    color:   androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
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
        // Status label
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for draft to begin…", color = TextSecondary, fontSize = 10.sp)
        }

        // "Who picks first?" label
        Text(
            "WHO PICKS FIRST?",
            color      = MLBBGold,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )

        // Ally (left) | Enemy (right) toggle
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ALLY — left
            TeamToggleBtn(
                emoji        = "🔵",
                label        = "ALLY",
                isSelected   = ourTeamFirst,
                selectedColor = MLBBTeal,
                modifier     = Modifier.weight(1f),
                onClick      = { ourTeamFirst = true }
            )
            // ENEMY — right
            TeamToggleBtn(
                emoji        = "🔴",
                label        = "ENEMY",
                isSelected   = !ourTeamFirst,
                selectedColor = ErrorRed,
                modifier     = Modifier.weight(1f),
                onClick      = { ourTeamFirst = false }
            )
        }

        // START DRAFT button
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

        SlotOverview(
            enemySlots = buildSlotList(session.enemyBansR1, session.enemyBansR2),
            ourSlots   = buildSlotList(session.ourBansR1, session.ourBansR2),
            enemyColor = ErrorRed, ourColor = MLBBTeal, label = "Bans"
        )
        SlotOverview(
            enemySlots = session.enemyPicks, ourSlots = session.ourPicks,
            enemyColor = ErrorRed, ourColor = MLBBTeal, label = "Picks"
        )
    }
}

@Composable
private fun TeamToggleBtn(
    emoji:         String,
    label:         String,
    isSelected:    Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
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

// ── ACTIVE DRAFT body (combined ban + pick) ───────────────────────────────────

/**
 * The unified panel shown during any active draft phase.
 *
 * It always renders BOTH the ban section and the pick section so the user sees
 * the full picture immediately after tapping the bubble:
 *
 *  ─── BAN ────────────────
 *  YOUR TURN TO BAN / Enemy banning...
 *  TOP BANS: [H1] [H2] [H3]
 *  ─── PICK ───────────────
 *  YOUR TURN / ENEMY TURN · Pick X/10
 *  TOP PICKS: [H1] [H2] [H3]
 *  ─── SLOTS ──────────────
 *  E bans ■■□  Y bans ■■□
 *  E picks ■□□ Y picks ■□□
 */
@Composable
private fun ActiveDraftBody(
    session:         DraftSession,
    recommendations: List<HeroScore>,
    banSuggestions:  List<BanSuggestion>,
    isBanTurn:       Boolean,
    enemyWarnings:   List<String>,
    onHeroSelected:  (Hero) -> Unit
) {
    val isPickTurn  = session.currentTurn?.side?.name == "OUR_TEAM"
    val pickLabel   = session.currentTurn?.let { "Pick ${it.pickNumber}/10" } ?: ""
    val isTrade     = session.phase == DraftPhase.TRADING

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // ── BAN section ──────────────────────────────────────────────────────
        SectionLabel("⛔  BAN", MLBBRed)

        // Turn indicator
        AnimatedVisibility(
            visible = isBanTurn,
            enter   = fadeIn(tween(150)) + slideInVertically { -it },
            exit    = fadeOut(tween(100)) + slideOutVertically { -it }
        ) {
            TurnBadge(
                text  = "YOUR TURN TO BAN",
                color = MLBBRed
            )
        }
        if (!isBanTurn) {
            Text("Enemy is banning…", color = TextSecondary, fontSize = 10.sp)
        }

        if (banSuggestions.isNotEmpty()) {
            HeroRow(
                label          = "TOP BANS",
                heroes         = banSuggestions.take(3).map { it.hero to it.badgeLabel },
                onHeroSelected = onHeroSelected
            )
        }

        // ── Divider ───────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBGold.copy(alpha = 0.12f)))

        // ── PICK section ─────────────────────────────────────────────────────
        SectionLabel("✅  PICK", SuccessGreen)

        if (isTrade) {
            Text(
                "Trading phase — tap heroes to swap",
                color    = WarningAmber,
                fontSize = 10.sp
            )
        } else {
            // Turn indicator
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
                    if (isPickTurn) "YOUR TURN" else "ENEMY TURN",
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
            HeroRow(
                label          = "TOP PICKS",
                heroes         = recommendations.take(3).map { it.hero to it.badgeLabel },
                onHeroSelected = onHeroSelected
            )
        }

        if (enemyWarnings.isNotEmpty()) {
            Text(
                enemyWarnings.first(),
                color    = WarningAmber,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Divider ───────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBGold.copy(alpha = 0.12f)))

        // ── Slot overview ────────────────────────────────────────────────────
        SlotOverview(
            enemySlots = buildSlotList(session.enemyBansR1, session.enemyBansR2),
            ourSlots   = buildSlotList(session.ourBansR1, session.ourBansR2),
            enemyColor = ErrorRed, ourColor = MLBBTeal, label = "Bans"
        )
        SlotOverview(
            enemySlots = session.enemyPicks, ourSlots = session.ourPicks,
            enemyColor = ErrorRed, ourColor = MLBBTeal, label = "Picks"
        )
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
            Text("Draft complete ✅", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Box(
                Modifier
                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("Close overlay", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text       = text,
        color      = color,
        fontSize   = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun TurnBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun HeroRow(
    label:          String,
    heroes:         List<Pair<Hero, String>>,
    onHeroSelected: (Hero) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MLBBGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            heroes.forEach { (hero, badge) ->
                QuickHeroChip(hero = hero, badge = badge, onTap = onHeroSelected)
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
            .padding(4.dp)
            .width(60.dp)
    ) {
        HeroPortrait(hero = hero, size = 44.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            hero.name,
            color    = TextPrimary,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(badge, color = MLBBGold, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SlotOverview(
    enemySlots: List<Hero?>,
    ourSlots:   List<Hero?>,
    enemyColor: androidx.compose.ui.graphics.Color,
    ourColor:   androidx.compose.ui.graphics.Color,
    label:      String
) {
    if (enemySlots.isEmpty() && ourSlots.isEmpty()) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        SlotDots(prefix = "E", slots = enemySlots, filledColor = enemyColor)
        Spacer(Modifier.width(8.dp))
        SlotDots(prefix = "Y", slots = ourSlots, filledColor = ourColor)
    }
}

@Composable
private fun SlotDots(
    prefix:      String,
    slots:       List<Hero?>,
    filledColor: androidx.compose.ui.graphics.Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("$prefix:", color = TextDisabled, fontSize = 8.sp)
        slots.forEach { hero ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (hero != null) filledColor else SurfaceElevated)
                    .border(0.5.dp, filledColor.copy(alpha = 0.4f), CircleShape)
            )
        }
    }
}

private fun buildSlotList(r1: List<Hero?>, r2: List<Hero?>): List<Hero?> =
    r1 + r2.filter { r2.isNotEmpty() }
