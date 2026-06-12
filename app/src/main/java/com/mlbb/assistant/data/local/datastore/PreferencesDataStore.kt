package com.mlbb.assistant.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
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
        val META_WEIGHT = doublePreferencesKey("meta_weight")
        val COUNTER_WEIGHT = doublePreferencesKey("counter_weight")
        val SYNERGY_WEIGHT = doublePreferencesKey("synergy_weight")
    }

    // Single shared data flow — all three weights from one subscription, not three
    private val prefsFlow = dataStore.data.distinctUntilChanged()

    val metaWeightFlow: Flow<Double> = prefsFlow.map { it[META_WEIGHT] ?: 0.5 }
    val counterWeightFlow: Flow<Double> = prefsFlow.map { it[COUNTER_WEIGHT] ?: 0.3 }
    val synergyWeightFlow: Flow<Double> = prefsFlow.map { it[SYNERGY_WEIGHT] ?: 0.2 }

    suspend fun saveWeights(meta: Double, counter: Double, synergy: Double) {
        dataStore.edit { prefs ->
            prefs[META_WEIGHT] = meta
            prefs[COUNTER_WEIGHT] = counter
            prefs[SYNERGY_WEIGHT] = synergy
        }
    }
}
