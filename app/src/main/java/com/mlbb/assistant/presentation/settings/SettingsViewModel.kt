package com.mlbb.assistant.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.datastore.PreferencesDataStore
import com.mlbb.assistant.domain.scoring.ScoreWeights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private var savedBannerJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                preferencesDataStore.metaWeightFlow,
                preferencesDataStore.counterWeightFlow,
                preferencesDataStore.synergyWeightFlow
            ) { meta, counter, synergy ->
                ScoreWeights(meta, counter, synergy)
            }.collect { weights ->
                _state.update {
                    it.copy(
                        metaWeight = weights.metaWeight,
                        counterWeight = weights.counterWeight,
                        synergyWeight = weights.synergyWeight
                    )
                }
            }
        }
    }

    fun saveWeights(meta: Double, counter: Double, synergy: Double) {
        // Clamp to [0,1] and normalise so weights always sum to 1.0
        val clamped = listOf(meta, counter, synergy).map { it.coerceIn(0.0, 1.0) }
        val total = clamped.sum().takeIf { it > 0.0 } ?: 1.0
        val nm = clamped[0] / total
        val nc = clamped[1] / total
        val ns = clamped[2] / total

        viewModelScope.launch {
            preferencesDataStore.saveWeights(nm, nc, ns)

            // Cancel any in-flight banner reset to avoid flicker on rapid saves
            savedBannerJob?.cancel()
            _state.update { it.copy(isSaved = true) }
            savedBannerJob = launch {
                delay(2000)
                _state.update { it.copy(isSaved = false) }
            }
        }
    }
}
