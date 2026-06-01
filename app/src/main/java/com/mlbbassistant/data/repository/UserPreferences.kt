package com.mlbbassistant.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_OVERLAY_ENABLED    = booleanPreferencesKey("overlay_enabled")
        private val KEY_OVERLAY_OPACITY    = floatPreferencesKey("overlay_opacity")
        private val KEY_SUGGESTION_COUNT   = stringPreferencesKey("suggestion_count")
        private val KEY_WEIGHT_META        = floatPreferencesKey("weight_meta")
        private val KEY_WEIGHT_COUNTER     = floatPreferencesKey("weight_counter")
        private val KEY_WEIGHT_SYNERGY     = floatPreferencesKey("weight_synergy")
    }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_OVERLAY_ENABLED] ?: false }

    val overlayOpacity: Flow<Float> = context.dataStore.data
        .map { it[KEY_OVERLAY_OPACITY] ?: 0.85f }

    val suggestionCount: Flow<Int> = context.dataStore.data
        .map { it[KEY_SUGGESTION_COUNT]?.toIntOrNull() ?: 5 }

    val weightMeta: Flow<Float>    = context.dataStore.data.map { it[KEY_WEIGHT_META]    ?: 0.35f }
    val weightCounter: Flow<Float> = context.dataStore.data.map { it[KEY_WEIGHT_COUNTER] ?: 0.40f }
    val weightSynergy: Flow<Float> = context.dataStore.data.map { it[KEY_WEIGHT_SYNERGY] ?: 0.25f }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        context.dataStore.edit { it[KEY_OVERLAY_OPACITY] = opacity.coerceIn(0.2f, 1f) }
    }

    suspend fun setSuggestionCount(count: Int) {
        context.dataStore.edit { it[KEY_SUGGESTION_COUNT] = count.coerceIn(1, 10).toString() }
    }

    suspend fun setWeights(meta: Float, counter: Float, synergy: Float) {
        context.dataStore.edit {
            it[KEY_WEIGHT_META]    = meta
            it[KEY_WEIGHT_COUNTER] = counter
            it[KEY_WEIGHT_SYNERGY] = synergy
        }
    }
}
