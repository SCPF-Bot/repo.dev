package com.mlbbassistant.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_OVERLAY_ENABLED   = booleanPreferencesKey("overlay_enabled")
        private val KEY_OVERLAY_OPACITY   = floatPreferencesKey("overlay_opacity")
        private val KEY_SUGGESTION_COUNT  = stringPreferencesKey("suggestion_count")
        private val KEY_WEIGHT_META       = floatPreferencesKey("weight_meta")
        private val KEY_WEIGHT_COUNTER    = floatPreferencesKey("weight_counter")
        private val KEY_WEIGHT_SYNERGY    = floatPreferencesKey("weight_synergy")
        // User-supplied remote API base URL. Empty string = disabled.
        val KEY_API_URL                   = stringPreferencesKey("api_url")
        private const val TAG = "UserPreferences"
    }

    /** Safe DataStore reader — emits defaults on IOException instead of crashing. */
    private val safeData = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "DataStore read error, using defaults", e)
                emit(emptyPreferences())
            } else throw e
        }

    val overlayEnabled:  Flow<Boolean> = safeData.map { it[KEY_OVERLAY_ENABLED]  ?: false }
    val overlayOpacity:  Flow<Float>   = safeData.map { it[KEY_OVERLAY_OPACITY]  ?: 0.85f }
    val suggestionCount: Flow<Int>     = safeData.map { it[KEY_SUGGESTION_COUNT]?.toIntOrNull() ?: 5 }
    val weightMeta:      Flow<Float>   = safeData.map { it[KEY_WEIGHT_META]      ?: 0.35f }
    val weightCounter:   Flow<Float>   = safeData.map { it[KEY_WEIGHT_COUNTER]   ?: 0.40f }
    val weightSynergy:   Flow<Float>   = safeData.map { it[KEY_WEIGHT_SYNERGY]   ?: 0.25f }
    /** Empty string means "no remote API configured — use bundled assets only". */
    val apiUrl:          Flow<String>  = safeData.map { it[KEY_API_URL]          ?: "" }

    suspend fun setOverlayEnabled(enabled: Boolean) = safeEdit {
        it[KEY_OVERLAY_ENABLED] = enabled
    }
    suspend fun setOverlayOpacity(opacity: Float) = safeEdit {
        it[KEY_OVERLAY_OPACITY] = opacity.coerceIn(0.2f, 1f)
    }
    suspend fun setSuggestionCount(count: Int) = safeEdit {
        it[KEY_SUGGESTION_COUNT] = count.coerceIn(1, 10).toString()
    }
    suspend fun setWeights(meta: Float, counter: Float, synergy: Float) = safeEdit {
        it[KEY_WEIGHT_META]    = meta
        it[KEY_WEIGHT_COUNTER] = counter
        it[KEY_WEIGHT_SYNERGY] = synergy
    }
    suspend fun setApiUrl(url: String) = safeEdit {
        it[KEY_API_URL] = url.trim()
    }

    private suspend fun safeEdit(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit(transform)
        } catch (e: IOException) {
            Log.e(TAG, "DataStore write error", e)
        } catch (e: Exception) {
            Log.e(TAG, "DataStore unexpected error", e)
        }
    }
}
