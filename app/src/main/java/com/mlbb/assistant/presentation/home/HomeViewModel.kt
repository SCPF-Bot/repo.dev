package com.mlbb.assistant.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Section 5.1.2 — "Your Insights" card data.
 *
 * Shown on the home screen once the user has completed ≥ 10 real (non-sim)
 * drafts with a recorded outcome.
 */
data class InsightsState(
    /** True when there are enough sessions to show insights. */
    val isAvailable: Boolean = false,
    /** Number of sessions with a recorded outcome (non-simulation). */
    val sessionCount: Int = 0,
    /** Sessions still needed before insights unlock (0 when unlocked). */
    val sessionsNeeded: Int = MIN_FOR_INSIGHTS,
    /** Win rate across all sessions with outcomes. Range [0, 100]. */
    val winRatePct: Int = 0,
    /** Recommendation follow rate. Range [0, 100]. */
    val recommendationFollowPct: Int = 0,
    /** Id of the hero picked most often across all sessions (-1 = none). */
    val topPickHeroId: Int = -1,
    /** Name of the top-picked hero (empty when unavailable). */
    val topPickHeroName: String = ""
) {
    companion object {
        const val MIN_FOR_INSIGHTS = 10
    }
}

data class HomeUiState(
    val topMetaHeroes: List<Hero> = emptyList(),
    val isLoading: Boolean = true,
    val insights: InsightsState = InsightsState()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val draftSessionDao: DraftSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getHeroesUseCase(),
                draftSessionDao.getAllSessions()
            ) { heroes, sessions ->
                val topMeta = heroes.sortedByDescending { it.winRate }.take(8)
                val insights = computeInsights(sessions)
                HomeUiState(topMetaHeroes = topMeta, isLoading = false, insights = insights)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ── Section 5.1.2 ─────────────────────────────────────────────────────────

    private fun computeInsights(sessions: List<DraftSessionEntity>): InsightsState {
        // Exclude simulation sessions — they don't represent real match outcomes.
        val real = sessions.filter { !it.isSimulation }
        val withOutcome = real.filter { DraftOutcome.fromString(it.outcome) != DraftOutcome.UNKNOWN }

        if (withOutcome.size < InsightsState.MIN_FOR_INSIGHTS) {
            return InsightsState(
                isAvailable   = false,
                sessionCount  = withOutcome.size,
                sessionsNeeded = (InsightsState.MIN_FOR_INSIGHTS - withOutcome.size).coerceAtLeast(0)
            )
        }

        val wins = withOutcome.count { DraftOutcome.fromString(it.outcome) == DraftOutcome.WIN }
        val winPct = wins * 100 / withOutcome.size

        val totalRec   = withOutcome.sumOf { it.totalRecommendations }
        val followedRec = withOutcome.sumOf { it.followedRecommendations }
        val followPct  = if (totalRec > 0) followedRec * 100 / totalRec else 0

        // Top-picked hero by frequency across all of our pick slots.
        val pickFreq = mutableMapOf<Int, Int>()
        withOutcome.forEach { s ->
            s.yourPickIds.filter { it >= 0 }.forEach { id ->
                pickFreq[id] = (pickFreq[id] ?: 0) + 1
            }
        }
        val topId = pickFreq.maxByOrNull { it.value }?.key ?: -1

        return InsightsState(
            isAvailable            = true,
            sessionCount           = withOutcome.size,
            sessionsNeeded         = 0,
            winRatePct             = winPct,
            recommendationFollowPct = followPct,
            topPickHeroId          = topId,
            topPickHeroName        = "" // resolved by screen via hero list if needed
        )
    }
}
