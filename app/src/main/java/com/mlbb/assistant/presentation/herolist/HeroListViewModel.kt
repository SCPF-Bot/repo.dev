package com.mlbb.assistant.presentation.herolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetPagedHeroesUseCase
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HeroListViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val syncHeroesUseCase: SyncHeroesUseCase,
    private val getPagedHeroesUseCase: GetPagedHeroesUseCase
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

    /**
     * TD-10: Paged hero stream wired to search query and lane/role filter.
     *
     * Uses [flatMapLatest] so any change to the search/role filter immediately
     * cancels the current page load and starts a fresh one. [cachedIn] keeps
     * the last page in memory across recompositions so the grid does not
     * reload when the screen re-enters the composition.
     *
     * The 150 ms [debounce] on [searchQueryFlow] matches the filter debounce
     * in [collectFilters] — rapid keystrokes do not trigger redundant DB queries.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedHeroes: Flow<PagingData<Hero>> = combine(
        searchQueryFlow.debounce(150L),
        selectedRoleFlow
    ) { query, role -> query to (role ?: "") }
        .flatMapLatest { (query, lane) ->
            getPagedHeroesUseCase(query = query, lane = lane)
        }
        .cachedIn(viewModelScope)

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
