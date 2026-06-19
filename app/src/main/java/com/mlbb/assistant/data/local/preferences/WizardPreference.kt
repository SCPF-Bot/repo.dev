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
 * Uses the same DataStore file name ("mlbb_preferences") as the one provided
 * by [com.mlbb.assistant.di.AppModule]. Using two different names would create
 * two separate DataStore files on disk — the wizard flag would live in one file
 * while score weights live in another, but both would bind to different
 * DataStore<Preferences> instances injected by Hilt. Keeping the name identical
 * ensures only one DataStore file is created for the entire app.
 *
 * Usage:
 *   // Read (in a Composable via produceState or collectAsStateWithLifecycle):
 *   WizardPreference.observe(context).collect { done -> ... }
 *
 *   // Write (from a coroutine scope, e.g. after onboarding completes):
 *   WizardPreference.setDone(context, done = true)
 */
object WizardPreference {

    private val Context.dataStore by preferencesDataStore(name = "mlbb_preferences")

    private val WIZARD_DONE = booleanPreferencesKey("wizard_done")

    fun observe(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[WIZARD_DONE] ?: false }

    suspend fun setDone(context: Context, done: Boolean) {
        context.dataStore.edit { prefs -> prefs[WIZARD_DONE] = done }
    }
}
