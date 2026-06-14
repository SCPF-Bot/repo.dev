package com.mlbb.assistant.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.presentation.common.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DraftHistoryScreen(
    sessions: List<DraftSessionEntity>,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceMid).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📜 DRAFT HISTORY", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("← Back", color = MLBBGold, fontSize = 12.sp, modifier = Modifier.clickable { onBack() })
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No drafts saved yet", color = TextSecondary, fontSize = 14.sp)
                    Text("Complete a draft to see it here", color = TextDisabled, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    DraftHistoryCard(session = session)
                }
            }
        }
    }
}

@Composable
private fun DraftHistoryCard(session: DraftSessionEntity) {
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val dateStr = fmt.format(Date(session.timestamp))

    val scoreColor = when {
        session.draftScore >= 80 -> SuccessGreen
        session.draftScore >= 60 -> WarningAmber
        else                     -> ErrorRed
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Draft #${session.id}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(dateStr, color = TextSecondary, fontSize = 10.sp)
                }
                Box(
                    Modifier
                        .background(scoreColor.copy(0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("${session.draftScore}/100", color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatBadge("Meta",    "${session.metaScore}%",    MLBBGold)
                StatBadge("Counter", "${session.counterScore}%", MLBBBlue)
                StatBadge("Synergy", "${session.synergyScore}%", MLBBTeal)
            }

            val recPct = if (session.totalRecommendations > 0)
                "${session.followedRecommendations}/${session.totalRecommendations} recs followed"
            else "No recommendations tracked"
            Text(recPct, color = TextDisabled, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text("$label: $value", color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}
