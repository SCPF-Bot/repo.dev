package com.mlbb.assistant.presentation.herolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HeroListViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val syncHeroesUseCase: SyncHeroesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HeroListState(isLoading = true))
    val state: StateFlow<HeroListState> = _state.asStateFlow()

    private var heroCollectJob: Job? = null

    init {
        collectHeroes()
        viewModelScope.launch {
            runCatching { syncHeroesUseCase() }
        }
    }

    private fun collectHeroes() {
        if (heroCollectJob?.isActive == true) return
        heroCollectJob = viewModelScope.launch {
            // getHeroesUseCase returns Flow<List<Hero>> — no .toDomain() mapping needed
            getHeroesUseCase().collect { heroes ->
                _state.update { s ->
                    s.copy(
                        heroes         = heroes,
                        filteredHeroes = applyFilters(heroes, s.searchQuery, s.selectedRole),
                        isLoading      = false
                    )
                }
            }
        }
    }

    fun onSearchQuery(query: String) {
        _state.update { s ->
            s.copy(searchQuery = query, filteredHeroes = applyFilters(s.heroes, query, s.selectedRole))
        }
    }

    fun onRoleFilter(role: String?) {
        _state.update { s ->
            s.copy(selectedRole = role, filteredHeroes = applyFilters(s.heroes, s.searchQuery, role))
        }
    }

    private fun applyFilters(heroes: List<Hero>, query: String, role: String?): List<Hero> =
        heroes
            .filter { role == null || it.role.equals(role, ignoreCase = true) }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
}
