package com.mlbbassistant.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbbassistant.core.DraftEngine
import com.mlbbassistant.data.model.DraftState
import com.mlbbassistant.data.model.DraftSuggestion
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.repository.HeroRepository
import com.mlbbassistant.data.repository.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val repo: HeroRepository,
    private val engine: DraftEngine,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _draftState = MutableStateFlow(DraftState())
    val draftState: StateFlow<DraftState> = _draftState.asStateFlow()

    private val allHeroes: StateFlow<List<Hero>> = repo.observeHeroes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Scoring config bundled from DataStore prefs. */
    private data class ScoringConfig(
        val topN: Int, val meta: Float, val counter: Float, val synergy: Float
    )

    private val scoringConfig: Flow<ScoringConfig> = combine(
        prefs.suggestionCount,
        prefs.weightMeta,
        prefs.weightCounter,
        prefs.weightSynergy
    ) { topN, meta, counter, synergy ->
        ScoringConfig(topN, meta, counter, synergy)
    }

    /** Suggestions recomputed whenever draft state, hero pool, or weights change. */
    val suggestions: StateFlow<List<DraftSuggestion>> = combine(
        _draftState,
        allHeroes,
        scoringConfig
    ) { state, heroes, config ->
        val pool = heroes.filter { it.id !in state.unavailableIds }
        engine.suggest(
            pool, state, config.topN,
            DraftEngine.Weights(config.meta, config.counter, config.synergy)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------------------------------------------------------------
    // Draft mutation helpers
    // -------------------------------------------------------------------------

    fun addAllyPick(hero: Hero)   = mutateDraft { copy(allyPicks  = allyPicks  + hero) }
    fun addEnemyPick(hero: Hero)  = mutateDraft { copy(enemyPicks = enemyPicks + hero) }
    fun addBan(hero: Hero)        = mutateDraft { copy(bans       = bans       + hero) }

    fun removeAllyPick(hero: Hero)  = mutateDraft { copy(allyPicks  = allyPicks  - hero) }
    fun removeEnemyPick(hero: Hero) = mutateDraft { copy(enemyPicks = enemyPicks - hero) }
    fun removeBan(hero: Hero)       = mutateDraft { copy(bans       = bans       - hero) }

    fun resetDraft() { _draftState.value = DraftState() }

    private fun mutateDraft(transform: DraftState.() -> DraftState) {
        viewModelScope.launch { _draftState.value = _draftState.value.transform() }
    }
}
