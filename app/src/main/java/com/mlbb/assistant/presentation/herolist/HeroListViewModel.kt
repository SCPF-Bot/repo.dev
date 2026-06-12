package com.mlbb.assistant.presentation.herolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HeroListViewModel @Inject constructor(
    private val getHeroesUseCase: GetHeroesUseCase,
    private val syncHeroesUseCase: SyncHeroesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HeroListState())
    val state: StateFlow<HeroListState> = _state

    private var heroCollectJob: Job? = null

    fun loadHeroes() {
        // Guard against multiple concurrent collectors from repeated LaunchedEffect calls
        if (heroCollectJob?.isActive == true) return
        heroCollectJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            syncHeroesUseCase().onFailure { error ->
                _state.value = _state.value.copy(error = error.message)
            }
            getHeroesUseCase()
                .catch { e ->
                    _state.value = _state.value.copy(error = e.message, isLoading = false)
                }
                .collect { heroes ->
                    _state.value = _state.value.copy(heroes = heroes, isLoading = false)
                }
        }
    }
}
