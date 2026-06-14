package com.mlbb.assistant.presentation.settings

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val syncHeroesUseCase: SyncHeroesUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        val KEY_META    = floatPreferencesKey("weight_meta")
        val KEY_COUNTER = floatPreferencesKey("weight_counter")
        val KEY_SYNERGY = floatPreferencesKey("weight_synergy")
        val KEY_OPACITY = floatPreferencesKey("overlay_opacity")
        val KEY_AUTO_SHOW     = booleanPreferencesKey("auto_show_overlay")
        val KEY_VOICE         = booleanPreferencesKey("voice_alerts")
        val KEY_AUTO_SYNC     = booleanPreferencesKey("auto_sync")
        val KEY_LAST_SYNCED   = stringPreferencesKey("last_synced")
        val KEY_DEFAULT_RANK  = stringPreferencesKey("default_rank")
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _state.update {
                    it.copy(
                        metaWeight       = prefs[KEY_META]    ?: 0.40f,
                        counterWeight    = prefs[KEY_COUNTER] ?: 0.30f,
                        synergyWeight    = prefs[KEY_SYNERGY] ?: 0.30f,
                        overlayOpacity   = prefs[KEY_OPACITY] ?: 0.87f,
                        autoShowOverlay  = prefs[KEY_AUTO_SHOW] ?: true,
                        voiceAlertsEnabled = prefs[KEY_VOICE] ?: false,
                        autoSync         = prefs[KEY_AUTO_SYNC] ?: true,
                        lastSyncedLabel  = prefs[KEY_LAST_SYNCED] ?: "Never",
                        defaultRank      = prefs[KEY_DEFAULT_RANK] ?: "Epic",
                        overlayGranted   = Settings.canDrawOverlays(context),
                        accessibilityGranted = isAccessibilityEnabled()
                    )
                }
            }
        }
    }

    fun setMetaWeight(v: Float)    = save { it[KEY_META]    = v }
    fun setCounterWeight(v: Float) = save { it[KEY_COUNTER] = v }
    fun setSynergyWeight(v: Float) = save { it[KEY_SYNERGY] = v }
    fun setOpacity(v: Float)       = save { it[KEY_OPACITY] = v }
    fun setAutoShow(v: Boolean)    = save { it[KEY_AUTO_SHOW] = v }
    fun setVoiceAlerts(v: Boolean) = save { it[KEY_VOICE]    = v }
    fun setAutoSync(v: Boolean)    = save { it[KEY_AUTO_SYNC] = v }

    fun resetWeights() = viewModelScope.launch {
        dataStore.edit {
            it[KEY_META]    = 0.40f
            it[KEY_COUNTER] = 0.30f
            it[KEY_SYNERGY] = 0.30f
        }
    }

    fun syncNow() = viewModelScope.launch {
        syncHeroesUseCase()
        val label = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
        dataStore.edit { it[KEY_LAST_SYNCED] = label }
    }

    private fun save(block: suspend (Preferences.Editor) -> Unit) =
        viewModelScope.launch { dataStore.edit { block(it) } }

    private fun isAccessibilityEnabled(): Boolean = runCatching {
        val service = "${context.packageName}/${com.mlbb.assistant.service.MLBBAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        enabled?.contains(service) == true
    }.getOrDefault(false)
}
