package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun RoleDashboard(
    picks: List<Hero>,
    modifier: Modifier = Modifier
) {
    val laneMap     = CompositionAnalyzer.getLanesFilled(picks)
    val missingLanes = CompositionAnalyzer.getMissingLanes(picks)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Lane.entries.forEach { lane ->
            LaneSlotIndicator(
                lane    = lane,
                hero    = laneMap[lane],
                missing = lane in missingLanes
            )
        }
    }
}

@Composable
private fun LaneSlotIndicator(lane: Lane, hero: Hero?, missing: Boolean) {
    val laneColor = laneColor(lane)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(52.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (missing) SurfaceElevated else laneColor.copy(alpha = 0.15f),
                    RoundedCornerShape(6.dp)
                )
                .border(1.dp, if (missing) ErrorRed.copy(alpha = 0.6f) else laneColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (hero != null) {
                androidx.compose.foundation.Image(
                    painter = coil3.compose.rememberAsyncImagePainter(hero.imageUrl),
                    contentDescription = hero.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    if (missing) "!" else "?",
                    color = if (missing) ErrorRed else TextDisabled,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            lane.name.take(3),
            color  = if (missing) ErrorRed else laneColor,
            fontSize = 9.sp,
            fontWeight = if (missing) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        if (missing) {
            Text("NEED", color = ErrorRed, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun laneColor(lane: Lane) = when (lane) {
    Lane.EXP    -> LaneExp
    Lane.JUNGLE -> LaneJungle
    Lane.MID    -> LaneMid
    Lane.GOLD   -> LaneGold
    Lane.ROAM   -> LaneRoam
}
