package com.example.mlbbdraftassistant.ui.overlay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mlbbdraftassistant.MLBBDraftAssistantApp
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.domain.Recommendation
import com.example.mlbbdraftassistant.domain.RecommendationEngine
import com.example.mlbbdraftassistant.domain.ScoringConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftState(
    val allies: List<Hero> = emptyList(),
    val enemies: List<Hero> = emptyList(),
    val availableHeroes: List<Hero> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = false
)

class DraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MLBBDraftAssistantApp).repository
    private val engine = RecommendationEngine(ScoringConfig.DEFAULT)

    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()

    init {
        // Load hero data and observe changes
        viewModelScope.launch {
            // Initialize repository (fetch + cache)
            (repository as com.example.mlbbdraftassistant.data.repository.HeroRepositoryImpl).initialize()
            repository.observeHeroes().collect { heroes ->
                _state.update { it.copy(availableHeroes = heroes) }
            }
        }
    }

    /**
     * Update the draft with current ally and enemy selections.
     * Typically called from the overlay UI when the user changes a pick.
     */
    fun updateDraft(allies: List<Hero>, enemies: List<Hero>) {
        _state.update { it.copy(allies = allies, enemies = enemies) }
        recompute()
    }

    /**
     * Force a refresh of hero data from the API.
     */
    fun refreshHeroData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.refreshHeroData()
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun recompute() {
        val current = _state.value
        if (current.availableHeroes.isEmpty()) return
        val recs = engine.recommend(
            allies = current.allies,
            enemies = current.enemies,
            availableHeroes = current.availableHeroes
        )
        _state.update { it.copy(recommendations = recs) }
    }
}