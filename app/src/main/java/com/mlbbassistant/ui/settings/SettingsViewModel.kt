package com.mlbbassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbbassistant.data.repository.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    val overlayEnabled  = prefs.overlayEnabled.catch  { emit(false)  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val overlayOpacity  = prefs.overlayOpacity.catch  { emit(0.85f)  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.85f)
    val suggestionCount = prefs.suggestionCount.catch { emit(5)      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
    val weightMeta      = prefs.weightMeta.catch      { emit(0.35f)  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)
    val weightCounter   = prefs.weightCounter.catch   { emit(0.40f)  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.40f)
    val weightSynergy   = prefs.weightSynergy.catch   { emit(0.25f)  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.25f)
    val apiUrl          = prefs.apiUrl.catch          { emit("")     }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setOverlayEnabled(e: Boolean)  = viewModelScope.launch { prefs.setOverlayEnabled(e) }
    fun setOverlayOpacity(v: Float)    = viewModelScope.launch { prefs.setOverlayOpacity(v) }
    fun setSuggestionCount(n: Int)     = viewModelScope.launch { prefs.setSuggestionCount(n) }
    fun setApiUrl(url: String)         = viewModelScope.launch { prefs.setApiUrl(url) }

    fun setWeights(meta: Float, counter: Float, synergy: Float) = viewModelScope.launch {
        val total = meta + counter + synergy
        if (total <= 0f) return@launch
        prefs.setWeights(meta / total, counter / total, synergy / total)
    }
}
