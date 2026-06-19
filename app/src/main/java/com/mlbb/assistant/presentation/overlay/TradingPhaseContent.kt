package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.components.RoleDashboard
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun TradingPhaseContent(
    session: DraftSession,
    onSwap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBackground, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔄 TRADING PHASE", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Box(
                Modifier
                    .background(WarningAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .border(1.dp, WarningAmber.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("~20 seconds", color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            "Swap request? Role assignments will update automatically.",
            color = TextSecondary, fontSize = 10.sp
        )

        // Final picks
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR FINAL TEAM", color = MLBBTeal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                session.ourPicks.forEachIndexed { i, hero ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HeroPortrait(hero = hero, size = 48.dp)
                        Text("S${i + 1}", color = TextDisabled, fontSize = 8.sp)
                    }
                }
            }

            Text("ENEMY TEAM", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                session.enemyPicks.forEach { hero ->
                    HeroPortrait(hero = hero, size = 48.dp)
                }
            }
        }

        // Role dashboard post-swap
        RoleDashboard(picks = session.ourPickedHeroes)

        Text(
            "Tap two of your heroes above to propose a swap.",
            color = TextDisabled, fontSize = 9.sp
        )
    }
}
