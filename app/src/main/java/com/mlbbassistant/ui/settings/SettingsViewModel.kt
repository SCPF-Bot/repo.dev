package com.mlbbassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbbassistant.data.repository.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    val overlayEnabled   = prefs.overlayEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val overlayOpacity   = prefs.overlayOpacity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.85f)
    val suggestionCount  = prefs.suggestionCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
    val weightMeta       = prefs.weightMeta.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)
    val weightCounter    = prefs.weightCounter.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.40f)
    val weightSynergy    = prefs.weightSynergy.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.25f)

    fun setOverlayEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setOverlayEnabled(enabled)
    }

    fun setOverlayOpacity(opacity: Float) = viewModelScope.launch {
        prefs.setOverlayOpacity(opacity)
    }

    fun setSuggestionCount(count: Int) = viewModelScope.launch {
        prefs.setSuggestionCount(count)
    }

    fun setWeights(meta: Float, counter: Float, synergy: Float) = viewModelScope.launch {
        val total = meta + counter + synergy
        if (total <= 0f) return@launch
        prefs.setWeights(meta / total, counter / total, synergy / total)
    }
}
