package com.mlbb.assistant.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBBlue
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many skeleton cards to show during the initial load delay. */
private const val SHIMMER_CARD_COUNT = 5

/**
 * Shimmer loading delay (ms).
 *
 * The sessions StateFlow emits [emptyList] synchronously on subscription
 * (the Room query has not yet resolved). Showing a short shimmer hides the
 * flash of the empty-state icon that would otherwise appear before data
 * arrives. 400 ms is deliberately short — on-device Room queries typically
 * resolve in <100 ms, so the shimmer is invisible on fast devices and only
 * appears on cold-start when IO is busy.
 */
private const val LOADING_DELAY_MS = 400L

@Composable
fun DraftHistoryScreen(
    onBack:        () -> Unit,
    onReplayClick: (Int) -> Unit = {},
    viewModel: DraftHistoryViewModel = viewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    // Show shimmer for a brief window to avoid empty-state flash.
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(LOADING_DELAY_MS)
        isLoading = false
    }

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
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

        when {
            isLoading -> HistoryLoadingSkeleton()

            sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ListAlt, contentDescription = null,
                        tint = TextDisabled, modifier = Modifier.size(48.dp))
                    Text("No drafts saved yet", color = TextSecondary, fontSize = 14.sp)
                    Text("Complete a draft to see it here", color = TextDisabled, fontSize = 12.sp)
                }
            }

            else -> LazyColumn(
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

/**
 * Shimmer skeleton shown for [LOADING_DELAY_MS] ms before real content renders.
 *
 * Cards mimic the height and structure of real [DraftHistoryCard] entries so
 * the transition is seamless.
 */
@Composable
private fun HistoryLoadingSkeleton() {
    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SHIMMER_CARD_COUNT) {
            HistorySkeletonCard()
        }
    }
}

@Composable
private fun HistorySkeletonCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(width = 80.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceMid))
                    Box(Modifier.size(width = 60.dp, height = 9.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceMid))
                }
                Box(Modifier.size(width = 64.dp, height = 26.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceMid))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(Modifier.size(width = 72.dp, height = 22.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceMid))
                }
            }
        }
    }
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

@Composable
private fun DraftHistoryCard(session: DraftHistoryItem) {
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

    androidx.compose.material3.Card(
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
                Box(
                    Modifier
                        .background(scoreColor.copy(0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${session.draftScore}/100", color = scoreColor,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
