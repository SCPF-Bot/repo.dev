package com.mlbb.assistant.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val META_WEIGHT     = floatPreferencesKey("meta_weight")
        val COUNTER_WEIGHT  = floatPreferencesKey("counter_weight")
        val SYNERGY_WEIGHT  = floatPreferencesKey("synergy_weight")
    }

    private val prefsFlow = dataStore.data.distinctUntilChanged()

    val metaWeightFlow:    Flow<Float> = prefsFlow.map { it[META_WEIGHT]    ?: 0.40f }
    val counterWeightFlow: Flow<Float> = prefsFlow.map { it[COUNTER_WEIGHT] ?: 0.30f }
    val synergyWeightFlow: Flow<Float> = prefsFlow.map { it[SYNERGY_WEIGHT] ?: 0.30f }

    suspend fun saveWeights(meta: Float, counter: Float, synergy: Float) {
        dataStore.edit { prefs ->
            prefs[META_WEIGHT]    = meta
            prefs[COUNTER_WEIGHT] = counter
            prefs[SYNERGY_WEIGHT] = synergy
        }
    }
}
