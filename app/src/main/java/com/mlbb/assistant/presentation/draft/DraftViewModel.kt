package com.mlbb.assistant.presentation.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.datastore.PreferencesDataStore
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            getHeroesUseCase().collect { heroes ->
                allHeroes = heroes
                _state.value = _state.value.copy(isLoading = false)
                updateSuggestions()
            }
        }
    }

    fun addAlly(name: String) {
        val hero = findHeroByName(name)
        if (hero != null && !_state.value.allies.contains(hero)) {
            _state.value = _state.value.copy(allies = _state.value.allies + hero)
            updateSuggestions()
        }
    }

    fun removeAlly(hero: Hero) {
        _state.value = _state.value.copy(allies = _state.value.allies - hero)
        updateSuggestions()
    }

    fun addEnemy(name: String) {
        val hero = findHeroByName(name)
        if (hero != null && !_state.value.enemies.contains(hero)) {
            _state.value = _state.value.copy(enemies = _state.value.enemies + hero)
            updateSuggestions()
        }
    }

    fun removeEnemy(hero: Hero) {
        _state.value = _state.value.copy(enemies = _state.value.enemies - hero)
        updateSuggestions()
    }

    fun addBan(name: String) {
        val hero = findHeroByName(name)
        if (hero != null && !_state.value.bans.contains(hero)) {
            _state.value = _state.value.copy(bans = _state.value.bans + hero)
            updateSuggestions()
        }
    }

    fun removeBan(hero: Hero) {
        _state.value = _state.value.copy(bans = _state.value.bans - hero)
        updateSuggestions()
    }

    private fun findHeroByName(name: String): Hero? {
        return allHeroes.find { it.name.equals(name, ignoreCase = true) }
    }

    private fun updateSuggestions() {
        val suggestions = getSuggestionsUseCase(
            allHeroes = allHeroes,
            allies = _state.value.allies,
            enemies = _state.value.enemies,
            weights = currentWeights,
            bannedIds = _state.value.bans.map { it.id }
        )
        _state.value = _state.value.copy(suggestions = suggestions)
    }
}