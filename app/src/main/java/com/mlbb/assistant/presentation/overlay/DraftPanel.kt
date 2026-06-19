package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.advisor.BuildAdvisor
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.advisor.DraftScoreCalculator
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.OverlayBackground
import com.mlbb.assistant.presentation.common.theme.TextSecondary

@Composable
fun DraftPanel(
    session: DraftSession,
    recommendations: List<HeroScore>,
    banSuggestions: List<BanSuggestion>,
    allHeroes: List<Hero>,
    enemyWarnings: List<String>,
    isBanTurn: Boolean,
    onHeroSelected: (Hero) -> Unit,
    onHeroLongPress: (Hero) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val isOurTurn = session.currentTurn?.side?.name == "OUR_TEAM"
    val pickLabel = session.currentTurn?.let { "Pick ${it.pickNumber} of 10" } ?: ""

    Box(
        Modifier
            .fillMaxWidth()
            .background(OverlayBackground, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.20f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
    ) {
        Column {
            PanelHeader(onMinimize = onMinimize, onClose = onClose)

            AnimatedContent(
                targetState = session.phase,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn(tween(250)) togetherWith
                    slideOutHorizontally { -it } + fadeOut(tween(200))
                },
                label = "phase_transition"
            ) { phase ->
                when (phase) {
                    DraftPhase.SETUP -> SetupContent()

                    DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 ->
                        BanPhaseContent(
                            phase              = phase,
                            banStructure       = session.banStructure,
                            enemyBansR1        = session.enemyBansR1,
                            ourBansR1          = session.ourBansR1,
                            enemyBansR2        = session.enemyBansR2,
                            ourBansR2          = session.ourBansR2,
                            banSuggestions     = banSuggestions,
                            allHeroes          = allHeroes,
                            disabledIds        = session.unavailableIds,
                            isBanButtonVisible = isBanTurn,
                            onHeroSelected     = onHeroSelected,
                            onHeroLongPress    = onHeroLongPress
                        )

                    DraftPhase.PICK ->
                        PickPhaseContent(
                            session         = session,
                            recommendations = recommendations,
                            allHeroes       = allHeroes,
                            enemyWarnings   = enemyWarnings,
                            isOurTurn       = isOurTurn,
                            pickLabel       = pickLabel,
                            onHeroTap       = onHeroSelected,
                            onHeroLongPress = onHeroLongPress
                        )

                    DraftPhase.TRADING ->
                        TradingPhaseContent(session = session, onSwap = { _, _ -> })

                    DraftPhase.COMPLETE -> {
                        val score = DraftScoreCalculator.calculate(
                            ourPicks     = session.ourPickedHeroes,
                            enemyPicks   = session.enemyPickedHeroes,
                            followedRecs = session.followedRecommendations,
                            totalRecs    = session.totalRecommendations
                        )
                        val ourCarry = session.ourPickedHeroes.firstOrNull()
                        val advice   = ourCarry?.let {
                            BuildAdvisor.getAdvice(it, CompositionAnalyzer.analyze(session.enemyPickedHeroes))
                        }
                        FinalReportContent(
                            draftScore  = score,
                            buildAdvice = advice,
                            onNewDraft  = { },
                            onClose     = onClose
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(onMinimize: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = "MLBB DRAFT ASSISTANT",
            color      = MLBBGold,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp,
            modifier   = Modifier.weight(1f)
        )
        // Minimize — 48dp touch target via IconButton (accessibility-compliant)
        IconButton(
            onClick  = onMinimize,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.HorizontalRule,
                contentDescription = "Minimize panel",
                tint               = TextSecondary,
                modifier           = Modifier.size(18.dp)
            )
        }
        // Close — 48dp touch target via IconButton
        IconButton(
            onClick  = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.Close,
                contentDescription = "Close panel",
                tint               = ErrorRed,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBGold.copy(alpha = 0.15f)))
}

@Composable
private fun SetupContent() {
    Box(
        Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color    = MLBBGold,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Preparing draft assistant...", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
