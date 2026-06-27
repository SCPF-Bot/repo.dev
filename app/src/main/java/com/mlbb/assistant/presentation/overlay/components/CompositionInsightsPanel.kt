package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.advisor.CompositionArchetype
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextSecondary

@Composable
internal fun CompositionInsightsPanel(session: DraftSession) {
    val hasPicks = session.ourPickedHeroes.isNotEmpty() || session.enemyPickedHeroes.isNotEmpty()
    if (!hasPicks) return

    val enemyArchetype = remember(session.enemyPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.enemyPickedHeroes)
    }
    val ourArchetype = remember(session.ourPickedHeroes) {
        CompositionAnalyzer.detectArchetype(session.ourPickedHeroes)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(MLBBGold.copy(alpha = 0.18f)))
            Text("COMP INSIGHTS", color = MLBBGold, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            Box(Modifier.weight(1f).height(1.dp).background(MLBBGold.copy(alpha = 0.18f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ArchetypeChip(prefix = "Enemy", archetype = enemyArchetype, chipColor = ErrorRed)
            ArchetypeChip(prefix = "Ours",  archetype = ourArchetype,   chipColor = MLBBTeal)
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
                    Text(ourArchetype.winCondition, color = TextSecondary, fontSize = 7.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
