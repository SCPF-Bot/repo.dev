package com.mlbb.assistant.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DraftHistoryScreen(
    onBack: () -> Unit,
    viewModel: DraftHistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BackButton(onBack = onBack)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null,
                    tint = MLBBGold, modifier = Modifier.size(18.dp))
                Text("DRAFT HISTORY", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.size(48.dp))
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.ListAlt, contentDescription = null,
                        tint = TextDisabled, modifier = Modifier.size(48.dp))
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

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

@Composable
private fun DraftHistoryCard(session: DraftSessionEntity) {
    // java.time replaces SimpleDateFormat — thread-safe and minSdk 29+ safe
    val dateStr = remember(session.timestamp) {
        Instant.ofEpochMilli(session.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DATE_FORMATTER)
    }

    val scoreColor = when {
        session.draftScore >= 80 -> SuccessGreen
        session.draftScore >= 60 -> WarningAmber
        else                     -> ErrorRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Draft #${session.id}", color = TextPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(dateStr, color = TextSecondary, fontSize = 10.sp)
                }
                // Score badge — sized to not overflow at 100
                Box(
                    Modifier
                        .background(scoreColor.copy(0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${session.draftScore}/100", color = scoreColor,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Stat chips — increased from 9sp (unreadable) to SuggestionChip's labelMedium
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
    SuggestionChip(
        onClick = {},
        label   = { Text("$label: $value", style = MaterialTheme.typography.labelMedium) },
        colors  = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.10f),
            labelColor     = color
        ),
        border  = SuggestionChipDefaults.suggestionChipBorder(
            enabled     = true,
            borderColor = color.copy(alpha = 0.30f)
        )
    )
}
