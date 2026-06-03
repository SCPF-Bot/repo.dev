package com.mlbbassistant.ui.heroes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbbassistant.core.Resource
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroRole
import com.mlbbassistant.data.repository.HeroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HeroesViewModel @Inject constructor(
    private val repo: HeroRepository
) : ViewModel() {

    private val _searchQuery  = MutableStateFlow("")
    private val _selectedRole = MutableStateFlow<HeroRole?>(null)
    private val _refreshState = MutableStateFlow<Resource<Unit>?>(null)

    val searchQuery:  StateFlow<String>          = _searchQuery.asStateFlow()
    val selectedRole: StateFlow<HeroRole?>       = _selectedRole.asStateFlow()
    val refreshState: StateFlow<Resource<Unit>?> = _refreshState.asStateFlow()

    val heroes: StateFlow<List<Hero>> = combine(
        _searchQuery.debounce(300),
        _selectedRole
    ) { q, role -> q to role }
        .flatMapLatest { (q, role) ->
            when {
                q.isNotBlank() -> repo.searchHeroes(q)
                role != null   -> repo.observeByRole(role)
                else           -> repo.observeHeroes()
            }
        }
        .catch { emit(emptyList()) }           // never let flow crash the VM
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setRoleFilter(role: HeroRole?) { _selectedRole.value = role }

    fun refresh() {
        if (_refreshState.value is Resource.Loading) return   // debounce double-tap
        viewModelScope.launch {
            _refreshState.value = Resource.Loading
            _refreshState.value = repo.refreshHeroes()
        }
    }

    fun consumeRefreshState() { _refreshState.value = null }
}
