package com.mlbb.assistant.presentation.herolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HeroListViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val syncHeroesUseCase: SyncHeroesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HeroListState(isLoading = true))
    val state: StateFlow<HeroListState> = _state.asStateFlow()

    /**
     * Separate hot flows for search query and role filter so we can apply
     * [debounce] to keystrokes without delaying role-filter taps.
     */
    private val searchQueryFlow = MutableStateFlow("")
    private val selectedRoleFlow = MutableStateFlow<String?>(null)

    private var heroCollectJob: Job? = null

    init {
        collectHeroes()
        collectFilters()
        viewModelScope.launch {
            runCatching { syncHeroesUseCase() }
        }
    }

    private fun collectHeroes() {
        if (heroCollectJob?.isActive == true) return
        heroCollectJob = viewModelScope.launch {
            getHeroesUseCase().collect { heroes ->
                val filtered = withContext(Dispatchers.Default) {
                    applyFilters(heroes, searchQueryFlow.value, selectedRoleFlow.value)
                }
                _state.update { s ->
                    s.copy(heroes = heroes, filteredHeroes = filtered, isLoading = false)
                }
            }
        }
    }

    /**
     * Combines search query (debounced 150 ms) with role filter and recomputes
     * [HeroListState.filteredHeroes] on [Dispatchers.Default] to avoid blocking
     * the Main thread when the hero list is large.
     */
    private fun collectFilters() {
        viewModelScope.launch {
            combine(
                searchQueryFlow.debounce(150L),
                selectedRoleFlow
            ) { query, role -> query to role }
                .collect { (query, role) ->
                    val filtered = withContext(Dispatchers.Default) {
                        applyFilters(_state.value.heroes, query, role)
                    }
                    _state.update { it.copy(filteredHeroes = filtered) }
                }
        }
    }

    /**
     * Called on every keystroke from the search field.
     * Updates [HeroListState.searchQuery] immediately for UI reactivity,
     * then emits to [searchQueryFlow] which is debounced before filtering.
     */
    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchQueryFlow.value = query
    }

    /**
     * Role filter taps are not debounced — the user expects an immediate response.
     */
    fun onRoleFilter(role: String?) {
        _state.update { it.copy(selectedRole = role) }
        selectedRoleFlow.value = role
    }

    private fun applyFilters(heroes: List<Hero>, query: String, role: String?): List<Hero> =
        heroes
            .filter { role == null || it.role.equals(role, ignoreCase = true) }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
}
