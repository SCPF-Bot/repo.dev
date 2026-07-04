package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.OverlayBackground
import com.mlbb.assistant.presentation.overlay.components.ActiveDraftBody
import com.mlbb.assistant.presentation.overlay.components.BottomActionBar
import com.mlbb.assistant.presentation.overlay.components.CompleteBody
import com.mlbb.assistant.presentation.overlay.components.IdleBody
import com.mlbb.assistant.presentation.overlay.components.WidgetHeader
import com.mlbb.assistant.presentation.overlay.components.WidgetScorePanel

/**
 * Expanded mini-widget (3:2 aspect ratio, 300×200 dp, scrollable content).
 *
 * This file is the orchestrator only — all sub-composables live in `components/`:
 *  - [WidgetHeader]           — header bar with phase label and control buttons
 *  - [ActiveDraftBody]        — ban/pick phase content with phase panels
 *  - [IdleBody]               — waiting state with team-first picker
 *  - [CompleteBody]           — draft-done state
 *  - [BottomActionBar]        — Minimize / Undo / Score / Next-Phase buttons
 *  - [WidgetScorePanel]       — inline 📊 score/insight panel
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
    onStartDraft:     (ourTeamFirst: Boolean) -> Unit,
    onNextPhase:      () -> Unit = {},
    captureUnavailable: Boolean = false,
    accessibilityOff:   Boolean = false,
    metaStaleDays:      Int?    = null
) {
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

    Box(
        modifier = Modifier
            .width(300.dp)
            .height(200.dp)
            .background(OverlayBackground, RoundedCornerShape(14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
    ) {
        Column {
            WidgetHeader(
                phaseLabel     = phaseLabel,
                isDraftActive  = isDraftActive,
                onMinimize     = onMinimize,
                onClose        = onClose,
                onRestartDraft = onRestartDraft
            )

            HRule(alpha = 0.15f)

            StatusBanners(
                captureUnavailable = captureUnavailable,
                accessibilityOff   = accessibilityOff,
                metaStaleDays      = metaStaleDays
            )

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
                            WidgetScorePanel(
                                session         = session,
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

            if (isDraftActive) {
                val nextPhaseLabel = when (session.phase) {
                    DraftPhase.BAN_ROUND_1 -> if (session.banStructure.hasRound2) "→ BAN R2" else "→ PICK"
                    DraftPhase.BAN_ROUND_2 -> "→ PICK"
                    DraftPhase.PICK        -> "→ TRADE"
                    DraftPhase.TRADING     -> "→ DONE"
                    else                   -> null
                }
                HRule(alpha = 0.12f)
                BottomActionBar(
                    canUndo        = session.undoStack.isNotEmpty(),
                    scoreActive    = showScorePanel,
                    nextPhaseLabel = nextPhaseLabel,
                    onMinimize     = onMinimize,
                    onUndo         = onUndo,
                    onScore        = { showScorePanel = !showScorePanel },
                    onNextPhase    = onNextPhase
                )
            }
        }
    }
}

// ── Shared widget helpers ──────────────────────────────────────────────────────

@Composable
internal fun HRule(alpha: Float = 0.15f) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBGold.copy(alpha = alpha)))
}

/**
 * todo.md §7 — Self-status banners: "capture unavailable", "meta data stale
 * (N days)", "accessibility service off". Stacked so more than one condition
 * can be surfaced at once (e.g. capture revoked AND accessibility off).
 * Backed by [OverlayStateHolder.captureUnavailable] / `.accessibilityOff` /
 * `.metaStaleDays`, written by [OverlayCaptureCoordinator] and the
 * accessibility watchdog respectively.
 */
@Composable
internal fun StatusBanners(
    captureUnavailable: Boolean,
    accessibilityOff:   Boolean,
    metaStaleDays:      Int?
) {
    val staleThresholdDays = 7
    val messages = buildList {
        if (captureUnavailable) add("⚠ Capture unavailable — re-open the app to resume detection")
        if (accessibilityOff) add("⚠ Accessibility service off — manual entry only")
        if (metaStaleDays != null && metaStaleDays >= staleThresholdDays) {
            add("ℹ Meta data stale ($metaStaleDays days)")
        }
    }
    if (messages.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        messages.forEach { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MLBBGold.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Text(
                    text     = msg,
                    color    = MLBBGold,
                    fontSize = 11.sp
                )
            }
        }
        HRule(alpha = 0.12f)
    }
}
