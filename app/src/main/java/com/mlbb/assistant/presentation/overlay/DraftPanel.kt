package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.mlbb.assistant.presentation.common.theme.*

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
    val isOurTurn   = session.currentTurn?.side?.name == "OUR_TEAM"
    val pickLabel   = session.currentTurn?.let { "Pick ${it.pickNumber} of 10" } ?: ""

    Box(
        Modifier
            .fillMaxWidth()
            .background(OverlayBackground, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .border(1.dp, MLBBGold.copy(alpha = 0.20f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
    ) {
        Column {
            // Top drag handle + controls
            PanelHeader(onMinimize = onMinimize, onClose = onClose)

            // Phase-specific content
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
                            ourPicks    = session.ourPickedHeroes,
                            enemyPicks  = session.enemyPickedHeroes,
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
                            onNewDraft  = { /* handled by VM */ },
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚔️  MLBB DRAFT ASSISTANT", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton("—", TextSecondary) { onMinimize() }
            PanelButton("✕", ErrorRed)     { onClose() }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MLBBGold.copy(alpha = 0.15f)))
}

@Composable
private fun PanelButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SetupContent() {
    Box(
        Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⏳", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Preparing draft assistant...", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
