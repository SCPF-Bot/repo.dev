package com.mlbb.assistant.presentation.heropool

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.database.HeroPoolDao
import com.mlbb.assistant.data.local.database.HeroPoolEntity
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Proficiency
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HeroPoolEntry(
    val hero:        Hero,
    val proficiency: Proficiency
)

/**
 * TD-07: [searchQuery] and [roleFilter] survive process death via
 * [SavedStateHandle], so the user's search/filter state is restored if the
 * app is killed in the background.
 */
@Immutable
data class HeroPoolState(
    val entries: List<HeroPoolEntry> = emptyList(),
    val isLoading: Boolean = true,
    /** The active hero name search filter. */
    val searchQuery: String = "",
    /** Optional role/lane filter; null means "show all roles". */
    val roleFilter: String? = null
)

private const val KEY_SEARCH_QUERY = "hero_pool_search"
private const val KEY_ROLE_FILTER  = "hero_pool_role_filter"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HeroPoolViewModel @Inject constructor(
    private val heroPoolDao: HeroPoolDao,
    getHeroesUseCase: GetHeroesUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * TD-07: Active search query backed by [SavedStateHandle] so it survives
     * configuration changes and process death.
     */
    private val searchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")

    /**
     * TD-07: Optional role filter backed by [SavedStateHandle].
     */
    private val roleFilter: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_ROLE_FILTER, null)

    val state: StateFlow<HeroPoolState> = combine(
        getHeroesUseCase(),
        heroPoolDao.getAll(),
        searchQuery,
        roleFilter
    ) { heroes, poolEntities, query, role ->
        val poolMap = poolEntities.associateBy { it.heroId }
        val entries = heroes
            .filter { it.id in poolMap }
            .filter { hero ->
                (query.isBlank() || hero.name.contains(query.trim(), ignoreCase = true)) &&
                (role == null || hero.lane.name.equals(role, ignoreCase = true))
            }
            .map { hero ->
                HeroPoolEntry(
                    hero        = hero,
                    proficiency = poolMap[hero.id]?.toProficiency() ?: Proficiency.COMFORTABLE
                )
            }
        HeroPoolState(
            entries     = entries,
            isLoading   = false,
            searchQuery = query,
            roleFilter  = role
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HeroPoolState()
    )

    // ── TD-07: Search & filter mutations ──────────────────────────────────────

    /** Updates the name search query. Survives process death. */
    fun setSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    /** Sets an optional lane/role filter. Pass null to clear. */
    fun setRoleFilter(role: String?) {
        savedStateHandle[KEY_ROLE_FILTER] = role
    }

    /** Clears both the search query and role filter. */
    fun clearFilters() {
        savedStateHandle[KEY_SEARCH_QUERY] = ""
        savedStateHandle[KEY_ROLE_FILTER]  = null
    }

    // ── Pool mutations ────────────────────────────────────────────────────────

    /**
     * Add a hero to the pool with the given proficiency, or update an
     * existing entry.  Uses REPLACE conflict strategy so upsert is safe.
     */
    fun setHeroProficiency(heroId: Int, proficiency: Proficiency) {
        viewModelScope.launch {
            heroPoolDao.upsert(HeroPoolEntity(heroId = heroId, proficiency = proficiency.name))
        }
    }

    /** Remove a hero from the personal pool. */
    fun removeHero(heroId: Int) {
        viewModelScope.launch {
            heroPoolDao.delete(heroId)
        }
    }

    /** Wipe the entire pool. */
    fun clearPool() {
        viewModelScope.launch {
            heroPoolDao.clearAll()
        }
    }
}
