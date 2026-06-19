package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
 * Compact "mini widget" that the floating bubble morphs into when tapped.
 *
 * Layout contract:
 *  ┌─────────────────────────────────────────────────┐
 *  │  MLBB DRAFT · PHASE LABEL          [─]  [✕]   │ ← drag handle / header
 *  │  divider                                        │
 *  │  turn indicator (YOUR / ENEMY TURN)             │
 *  │  top 3 hero recommendations (small portraits)   │
 *  │  slot-fill dots  E:[■][■][ ]  Y:[■][■][ ]      │
 *  └─────────────────────────────────────────────────┘
 *
 * Width is constrained to 280 dp so it always fits on any phone screen.
 * The whole widget is the drag target at the View level (OverlayService sets
 * the touch listener); individual composables handle their own clicks.
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
    onHeroSelected:  (Hero) -> Unit
) {
    val phaseLabel = when (session.phase) {
        DraftPhase.IDLE        -> "STANDBY"
        DraftPhase.SETUP       -> "SETUP"
        DraftPhase.BAN_ROUND_1 -> "BAN  R1"
        DraftPhase.BAN_ROUND_2 -> "BAN  R2"
        DraftPhase.PICK        -> "PICK"
        DraftPhase.TRADING     -> "TRADE"
        DraftPhase.COMPLETE    -> "DONE"
    }

    Box(
        modifier = Modifier
            .widthIn(min = 240.dp, max = 280.dp)
            .background(OverlayBackground, RoundedCornerShape(14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
    ) {
        Column {
            // ── Header (also serves as the visual drag indicator) ───────────
            WidgetHeader(phaseLabel = phaseLabel, onMinimize = onMinimize, onClose = onClose)

            // ── Divider ─────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MLBBGold.copy(alpha = 0.15f))
            )

            // ── Phase-specific body ─────────────────────────────────────────
            AnimatedContent(
                targetState    = session.phase,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "widget_phase"
            ) { phase ->
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (phase) {
                        DraftPhase.IDLE, DraftPhase.SETUP -> {
                            IdleBody()
                        }
                        DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                            BanBody(
                                session        = session,
                                banSuggestions = banSuggestions,
                                isBanTurn      = isBanTurn,
                                onHeroSelected = onHeroSelected
                            )
                        }
                        DraftPhase.PICK -> {
                            PickBody(
                                session         = session,
                                recommendations = recommendations,
                                enemyWarnings   = enemyWarnings,
                                onHeroSelected  = onHeroSelected
                            )
                        }
                        DraftPhase.TRADING -> {
                            TradingBody(session = session)
                        }
                        DraftPhase.COMPLETE -> {
                            CompleteBody(onClose = onClose)
                        }
                    }
                }
            }
        }
    }
}

// ── Header row ─────────────────────────────────────────────────────────────────

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
            // Drag-handle dots
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
            IconBtn(label = "x", color = ErrorRed, onClick = onClose)
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
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Phase bodies ───────────────────────────────────────────────────────────────

@Composable
private fun IdleBody() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Waiting for draft to begin...", color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun BanBody(
    session:        DraftSession,
    banSuggestions: List<BanSuggestion>,
    isBanTurn:      Boolean,
    onHeroSelected: (Hero) -> Unit
) {
    // Turn indicator
    AnimatedVisibility(
        visible = isBanTurn,
        enter   = fadeIn() + slideInVertically { -it },
        exit    = fadeOut() + slideOutVertically { -it }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MLBBRed.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .border(1.dp, MLBBRed.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "YOUR TURN TO BAN",
                color      = MLBBRed,
                fontWeight = FontWeight.Bold,
                fontSize   = 10.sp
            )
        }
    }
    if (!isBanTurn) {
        Text("Enemy is banning...", color = TextSecondary, fontSize = 10.sp)
    }

    // Top ban suggestions
    if (banSuggestions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TOP BANS", color = MLBBGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                banSuggestions.take(3).forEach { sug ->
                    QuickHeroChip(hero = sug.hero, badge = sug.badgeLabel, onTap = onHeroSelected)
                }
            }
        }
    }

    // Slot overview
    SlotOverview(
        enemySlots = buildSlotList(session.enemyBansR1, session.enemyBansR2),
        ourSlots   = buildSlotList(session.ourBansR1, session.ourBansR2),
        enemyColor = ErrorRed,
        ourColor   = MLBBTeal,
        label      = "Bans"
    )
}

@Composable
private fun PickBody(
    session:         DraftSession,
    recommendations: List<HeroScore>,
    enemyWarnings:   List<String>,
    onHeroSelected:  (Hero) -> Unit
) {
    val isOurTurn = session.currentTurn?.side?.name == "OUR_TEAM"
    val pickLabel = session.currentTurn?.let { "Pick ${it.pickNumber}/10" } ?: ""

    // Turn indicator
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isOurTurn) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.10f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            if (isOurTurn) "YOUR TURN" else "ENEMY TURN",
            color      = if (isOurTurn) SuccessGreen else ErrorRed,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold
        )
        if (pickLabel.isNotEmpty()) {
            Text(pickLabel, color = TextSecondary, fontSize = 9.sp)
        }
    }

    // Top pick suggestions
    if (recommendations.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TOP PICKS", color = MLBBGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recommendations.take(3).forEach { score ->
                    QuickHeroChip(hero = score.hero, badge = score.badgeLabel, onTap = onHeroSelected)
                }
            }
        }
    }

    // Enemy warning (first only to keep it compact)
    if (enemyWarnings.isNotEmpty()) {
        Text(
            enemyWarnings.first(),
            color    = WarningAmber,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    // Slot overview
    SlotOverview(
        enemySlots = session.enemyPicks,
        ourSlots   = session.ourPicks,
        enemyColor = ErrorRed,
        ourColor   = MLBBTeal,
        label      = "Picks"
    )
}

@Composable
private fun TradingBody(session: DraftSession) {
    Text("Trading phase", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
    Text("Tap heroes to swap positions", color = TextSecondary, fontSize = 10.sp)
    SlotOverview(
        enemySlots = session.enemyPicks,
        ourSlots   = session.ourPicks,
        enemyColor = ErrorRed,
        ourColor   = MLBBTeal,
        label      = "Picks"
    )
}

@Composable
private fun CompleteBody(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Draft complete", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Box(
                Modifier
                    .background(SuccessGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text("Close overlay", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

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

/**
 * Row of small filled/empty indicator dots for ban or pick slots.
 * E: enemy slots  Y: our slots
 */
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

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun buildSlotList(r1: List<Hero?>, r2: List<Hero?>): List<Hero?> =
    r1 + r2.filter { r2.isNotEmpty() }
