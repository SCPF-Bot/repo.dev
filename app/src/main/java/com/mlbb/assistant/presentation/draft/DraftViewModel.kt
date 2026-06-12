package com.mlbb.assistant.presentation.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.datastore.PreferencesDataStore
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val preferencesDataStore: PreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state

    private var allHeroes: List<Hero> = emptyList()
    private var currentWeights = ScoreWeights(0.5, 0.3, 0.2)
    private var heroCollectJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                preferencesDataStore.metaWeightFlow,
                preferencesDataStore.counterWeightFlow,
                preferencesDataStore.synergyWeightFlow
            ) { meta, counter, synergy ->
                ScoreWeights(meta, counter, synergy)
            }.collect { weights ->
                currentWeights = weights
                updateSuggestions()
            }
        }
    }

    fun loadHeroes() {
        heroCollectJob?.cancel()
        heroCollectJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getHeroesUseCase().collect { heroes ->
                allHeroes = heroes
                _state.update { it.copy(isLoading = false) }
                updateSuggestions()
            }
        }
    }

    fun addAlly(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val hero = findHeroByName(trimmed) ?: return
        val current = _state.value
        if (current.allies.any { it.id == hero.id } || current.enemies.any { it.id == hero.id }) return
        _state.update { it.copy(allies = it.allies + hero) }
        updateSuggestions()
    }

    fun removeAlly(hero: Hero) {
        _state.update { it.copy(allies = it.allies.filter { a -> a.id != hero.id }) }
        updateSuggestions()
    }

    fun addEnemy(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val hero = findHeroByName(trimmed) ?: return
        val current = _state.value
        if (current.enemies.any { it.id == hero.id } || current.allies.any { it.id == hero.id }) return
        _state.update { it.copy(enemies = it.enemies + hero) }
        updateSuggestions()
    }

    fun removeEnemy(hero: Hero) {
        _state.update { it.copy(enemies = it.enemies.filter { e -> e.id != hero.id }) }
        updateSuggestions()
    }

    fun addBan(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val hero = findHeroByName(trimmed) ?: return
        val current = _state.value
        if (current.bans.any { it.id == hero.id }) return
        _state.update { it.copy(bans = it.bans + hero) }
        updateSuggestions()
    }

    fun removeBan(hero: Hero) {
        _state.update { it.copy(bans = it.bans.filter { b -> b.id != hero.id }) }
        updateSuggestions()
    }

    private fun findHeroByName(name: String): Hero? =
        allHeroes.find { it.name.equals(name, ignoreCase = true) }

    /**
     * Computes draft suggestions on [Dispatchers.Default] to avoid blocking the Main thread.
     * Cancels any in-flight computation so only the latest state is used.
     */
    private fun updateSuggestions() {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch(Dispatchers.Default) {
            val s = _state.value
            val suggestions = getSuggestionsUseCase(
                allHeroes = allHeroes,
                allies = s.allies,
                enemies = s.enemies,
                weights = currentWeights,
                bannedIds = s.bans.map { it.id }
            )
            _state.update { it.copy(suggestions = suggestions) }
        }
    }
}
