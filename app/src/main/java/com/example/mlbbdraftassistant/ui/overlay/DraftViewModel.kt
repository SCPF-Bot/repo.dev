package com.example.mlbbdraftassistant.ui.overlay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mlbbdraftassistant.MLBBDraftAssistantApp
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.data.repository.HeroRepositoryImpl
import com.example.mlbbdraftassistant.domain.Recommendation
import com.example.mlbbdraftassistant.domain.RecommendationEngine
import com.example.mlbbdraftassistant.domain.ScoringConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftState(
    val allies: List<Hero?> = List(5) { null },
    val enemies: List<Hero?> = List(5) { null },
    val availableHeroes: List<Hero> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = false,
    val isLocked: Boolean = false
)

class DraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MLBBDraftAssistantApp).repository
    private val engine = RecommendationEngine(ScoringConfig.DEFAULT)

    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            (repository as HeroRepositoryImpl).initialize()
            repository.observeHeroes().collect { heroes ->
                _state.update { it.copy(availableHeroes = heroes) }
            }
        }
    }

    fun setAlly(slot: Int, hero: Hero) {
        _state.update { current ->
            val newAllies = current.allies.toMutableList()
            newAllies[slot] = hero
            current.copy(allies = newAllies)
        }
        recompute()
    }

    fun setEnemy(slot: Int, hero: Hero) {
        _state.update { current ->
            val newEnemies = current.enemies.toMutableList()
            newEnemies[slot] = hero
            current.copy(enemies = newEnemies)
        }
        recompute()
    }

    fun resetDraft() {
        _state.update {
            it.copy(allies = List(5) { null }, enemies = List(5) { null }, isLocked = false)
        }
        recompute()
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked) }
    }

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
        val alliesList = current.allies.filterNotNull()
        val enemiesList = current.enemies.filterNotNull()
        val recs = engine.recommend(
            allies = alliesList,
            enemies = enemiesList,
            availableHeroes = current.availableHeroes
        )
        _state.update { it.copy(recommendations = recs) }
    }
}