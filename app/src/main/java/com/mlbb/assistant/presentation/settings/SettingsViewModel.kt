package com.mlbb.assistant.presentation.settings

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.engine.WeightCalibrator
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetDraftHistoryUseCase
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val getDraftHistoryUseCase: GetDraftHistoryUseCase,
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

        /** Content URI of the user's ban-phase reference screenshot. */
        val KEY_BAN_SCREENSHOT_URI  = stringPreferencesKey("ban_screenshot_uri")

        /** JSON-serialised normalised tap positions mapped onto the ban-phase screenshot. */
        val KEY_SCREEN_MAPPING      = stringPreferencesKey("screen_mapping")
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _state.update {
                    it.copy(
                        metaWeight            = prefs[KEY_META]    ?: 0.40f,
                        counterWeight         = prefs[KEY_COUNTER] ?: 0.30f,
                        synergyWeight         = prefs[KEY_SYNERGY] ?: 0.30f,
                        overlayOpacity        = prefs[KEY_OPACITY] ?: 0.87f,
                        autoShowOverlay       = prefs[KEY_AUTO_SHOW] ?: true,
                        voiceAlertsEnabled    = prefs[KEY_VOICE] ?: false,
                        autoSync              = prefs[KEY_AUTO_SYNC] ?: true,
                        lastSyncedLabel       = prefs[KEY_LAST_SYNCED] ?: "Never",
                        defaultRank           = prefs[KEY_DEFAULT_RANK] ?: "6 bans (Epic)",
                        overlayGranted        = Settings.canDrawOverlays(context),
                        accessibilityGranted  = isAccessibilityEnabled(),
                        banPhaseScreenshotUri = prefs[KEY_BAN_SCREENSHOT_URI] ?: "",
                        screenMappingJson     = prefs[KEY_SCREEN_MAPPING]    ?: ""
                    )
                }
            }
        }
        // Section 5.2.2: Run calibration on init to show transparency data.
        runCalibration()
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

    // ── Section 5.2.2: Calibration transparency ───────────────────────────────

    /**
     * Reads recent session history and runs [WeightCalibrator] in the background.
     * Updates [SettingsState.calibrationResult] with the result.
     */
    fun runCalibration() {
        viewModelScope.launch {
            _state.update { it.copy(isCalibrating = true) }
            val history = getDraftHistoryUseCase.all().first()
            val currentWeights = ScoreWeights(
                meta    = _state.value.metaWeight,
                counter = _state.value.counterWeight,
                synergy = _state.value.synergyWeight
            )
            val result = WeightCalibrator.calibrate(history, currentWeights)
            _state.update { it.copy(calibrationResult = result, isCalibrating = false) }
        }
    }

    /**
     * Applies the calibration-suggested weights to DataStore.
     * Called when the user taps "Apply suggested weights".
     */
    fun applyCalibrationWeights() {
        val suggested = _state.value.calibrationResult?.suggestedWeights ?: return
        viewModelScope.launch {
            dataStore.edit {
                it[KEY_META]    = suggested.meta
                it[KEY_COUNTER] = suggested.counter
                it[KEY_SYNERGY] = suggested.synergy
            }
        }
    }

    fun setDefaultRank(rank: String)          = save { it[KEY_DEFAULT_RANK]       = rank }
    fun setBanPhaseScreenshotUri(uri: String) = save { it[KEY_BAN_SCREENSHOT_URI] = uri  }
    fun setScreenMapping(json: String)        = save { it[KEY_SCREEN_MAPPING]     = json }

    private fun save(block: suspend (MutablePreferences) -> Unit) =
        viewModelScope.launch { dataStore.edit { block(it) } }

    private fun isAccessibilityEnabled(): Boolean = runCatching {
        val service = "${context.packageName}/${com.mlbb.assistant.service.MLBBAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        enabled?.contains(service) == true
    }.getOrDefault(false)
}
