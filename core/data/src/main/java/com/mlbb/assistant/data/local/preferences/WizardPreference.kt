package com.mlbb.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mlbb.assistant.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Thin DataStore accessor for the onboarding wizard completion flag.
 *
 * Uses [com.mlbb.assistant.appDataStore] — the single authoritative DataStore
 * extension property defined in AppDataStore.kt.  Never declare a second
 * `preferencesDataStore` delegate for the same file name; DataStore throws
 * IllegalStateException if two instances are active on the same file.
 *
 * Usage:
 *   // Read (in a Composable via produceState or collectAsStateWithLifecycle):
 *   WizardPreference.observe(context).collect { done -> ... }
 *
 *   // Write (from a coroutine scope, e.g. after onboarding completes):
 *   WizardPreference.setDone(context, done = true)
 */
object WizardPreference {

    private val WIZARD_DONE = booleanPreferencesKey("wizard_done")

    fun observe(context: Context): Flow<Boolean> =
        context.appDataStore.data.map { prefs -> prefs[WIZARD_DONE] ?: false }

    suspend fun setDone(context: Context, done: Boolean) {
        context.appDataStore.edit { prefs -> prefs[WIZARD_DONE] = done }
    }
}
