package com.mlbb.assistant.presentation.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val draftSessionManager: DraftSessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()

    private var allHeroes: List<Hero> = emptyList()
    private var currentWeights = ScoreWeights.DEFAULT
    private var heroCollectJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        loadHeroes()
        viewModelScope.launch {
            draftSessionManager.session.collect { session ->
                _state.update { s ->
                    s.copy(
                        allies   = session.ourPickedHeroes,
                        enemies  = session.enemyPickedHeroes,
                        bans     = session.allBannedHeroes,
                        isLoading = false
                    )
                }
                refreshSuggestions(session)
            }
        }
    }

    private fun loadHeroes() {
        if (heroCollectJob?.isActive == true) return
        heroCollectJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // getHeroesUseCase returns Flow<List<Hero>> — no .toDomain() mapping needed
            getHeroesUseCase().collect { heroes ->
                allHeroes = heroes
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setWeights(meta: Float, counter: Float, synergy: Float) {
        currentWeights = ScoreWeights.normalized(meta = meta, synergy = synergy, counter = counter)
        refreshSuggestions(draftSessionManager.session.value)
    }

    private fun refreshSuggestions(session: DraftSession) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.Default) {
            val scored = getSuggestionsUseCase(allHeroes, session, currentWeights)
            _state.update { it.copy(suggestions = scored.map { h -> h.hero to h.totalScore.toDouble() }) }
        }
    }
}
