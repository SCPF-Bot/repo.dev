package com.mlbb.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Thin DataStore accessor for the onboarding wizard completion flag.
 *
 * Replaces the previous [android.content.SharedPreferences] usage in [AppShell]
 * and [AppNavGraph] so the entire app uses a single async preferences solution.
 *
 * Usage:
 *   // Read (in a Composable via produceState or collectAsStateWithLifecycle):
 *   WizardPreference.observe(context).collect { done -> ... }
 *
 *   // Write (from a coroutine scope, e.g. after onboarding completes):
 *   WizardPreference.setDone(context, done = true)
 */
object WizardPreference {

    private val Context.dataStore by preferencesDataStore(name = "mlbb_prefs")

    private val WIZARD_DONE = booleanPreferencesKey("wizard_done")

    fun observe(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[WIZARD_DONE] ?: false }

    suspend fun setDone(context: Context, done: Boolean) {
        context.dataStore.edit { prefs -> prefs[WIZARD_DONE] = done }
    }
}
