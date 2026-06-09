package com.mlbb.assistant.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.datastore.PreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            preferencesDataStore.metaWeightFlow.collectLatest { meta ->
                _state.value = _state.value.copy(metaWeight = meta)
            }
        }
        viewModelScope.launch {
            preferencesDataStore.counterWeightFlow.collectLatest { counter ->
                _state.value = _state.value.copy(counterWeight = counter)
            }
        }
        viewModelScope.launch {
            preferencesDataStore.synergyWeightFlow.collectLatest { synergy ->
                _state.value = _state.value.copy(synergyWeight = synergy)
            }
        }
    }

    fun saveWeights(meta: Double, counter: Double, synergy: Double) {
        viewModelScope.launch {
            preferencesDataStore.saveWeights(meta, counter, synergy)
            _state.value = _state.value.copy(isSaved = true)
            kotlinx.coroutines.delay(2000)
            _state.value = _state.value.copy(isSaved = false)
        }
    }
}