package com.mlbb.assistant.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mlbb.assistant.R
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class DraftReplayState(
    val session:   DraftSessionEntity? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DraftReplayViewModel @Inject constructor(
    private val dao: DraftSessionDao
) : ViewModel() {

    private val _state = MutableStateFlow(DraftReplayState())
    val state: StateFlow<DraftReplayState> = _state.asStateFlow()

    fun load(sessionId: Int) {
        viewModelScope.launch {
            val entity = dao.getSessionById(sessionId)
            _state.value = DraftReplayState(session = entity, isLoading = false)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftReplayScreen(
    sessionId: Int,
    onBack:    () -> Unit,
    vm: DraftReplayViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(sessionId) { vm.load(sessionId) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.draft_replay_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MLBBGold)
                }
            }
            state.session == null -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.draft_replay_not_found),
                        color = TextSecondary
                    )
                }
            }
            else -> {
                val s = state.session!!
                LazyColumn(
                    contentPadding = PaddingValues(
                        top    = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        start  = 16.dp,
                        end    = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── Summary header ────────────────────────────────────────
                    item {
                        ReplaySummaryCard(session = s)
                    }

                    // ── Ban round 1 ───────────────────────────────────────────
                    item {
                        SectionHeader(stringResource(R.string.draft_replay_ban_r1))
                    }
                    itemsIndexed(
                        buildBanTimeline(s.enemyBanIds, s.yourBanIds)
                    ) { _, item ->
                        ReplaySlotRow(label = item.first, heroId = item.second, color = MLBBRed)
                    }

                    // ── Picks ─────────────────────────────────────────────────
                    item {
                        SectionHeader(stringResource(R.string.draft_replay_picks))
                    }
                    itemsIndexed(
                        buildPickTimeline(s.enemyPickIds, s.yourPickIds)
                    ) { _, item ->
                        ReplaySlotRow(label = item.first, heroId = item.second, color = MLBBTeal)
                    }

                    // ── Score ─────────────────────────────────────────────────
                    item {
                        ScoreSummaryCard(session = s)
                    }
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun ReplaySummaryCard(session: DraftSessionEntity) {
    val fmt = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    val outcome = DraftOutcome.fromString(session.outcome)

    Box(
        Modifier
            .fillMaxWidth()
            .background(SurfaceMid, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    fmt.format(Date(session.timestamp)),
                    color    = TextSecondary,
                    fontSize = 12.sp
                )
                OutcomeBadge(outcome)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rank: ${session.rank}", color = MLBBGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (session.ourTeamFirst) "First pick" else "Second pick",
                    color = TextSecondary, fontSize = 13.sp
                )
                if (session.isSimulation) {
                    Text(
                        "SIM",
                        color = TextSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(SurfaceMid.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeBadge(outcome: DraftOutcome) {
    val (text, color) = when (outcome) {
        DraftOutcome.WIN     -> "WIN"  to SuccessGreen
        DraftOutcome.LOSS    -> "LOSS" to ErrorRed
        DraftOutcome.DRAW    -> "DRAW" to MLBBGold
        DraftOutcome.UNKNOWN -> "—"    to TextSecondary
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        color      = MLBBGold,
        fontSize   = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ReplaySlotRow(label: String, heroId: Int, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceMid, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text     = "Hero #$heroId",
            color    = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ScoreSummaryCard(session: DraftSessionEntity) {
    val advisedPct = if (session.totalRecommendations > 0)
        session.followedRecommendations * 100 / session.totalRecommendations
    else 0

    Box(
        Modifier
            .fillMaxWidth()
            .background(SurfaceMid, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.draft_replay_scores),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreStat("Draft",   session.draftScore)
                ScoreStat("Meta",    session.metaScore)
                ScoreStat("Counter", session.counterScore)
                ScoreStat("Synergy", session.synergyScore)
            }
            Text(
                "Followed recommendations: $advisedPct%",
                color    = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ScoreStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = "$value",
            color      = MLBBGold,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp
        )
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

// ── Timeline builders ─────────────────────────────────────────────────────────

private fun buildBanTimeline(
    enemyIds: List<Int>,
    ourIds:   List<Int>
): List<Pair<String, Int>> = buildList {
    enemyIds.forEachIndexed { i, id -> if (id >= 0) add("Enemy Ban ${i + 1}" to id) }
    ourIds.forEachIndexed   { i, id -> if (id >= 0) add("Our Ban ${i + 1}"   to id) }
}

private fun buildPickTimeline(
    enemyIds: List<Int>,
    ourIds:   List<Int>
): List<Pair<String, Int>> = buildList {
    enemyIds.forEachIndexed { i, id -> if (id >= 0) add("Enemy Pick ${i + 1}" to id) }
    ourIds.forEachIndexed   { i, id -> if (id >= 0) add("Our Pick ${i + 1}"   to id) }
}
