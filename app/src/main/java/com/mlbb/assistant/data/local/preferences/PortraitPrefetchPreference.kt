package com.mlbb.assistant.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mlbb.assistant.appDataStore
import kotlinx.coroutines.flow.first

/**
 * Thin DataStore accessor for the one-time hero-portrait prefetch flag.
 *
 * Set once [com.mlbb.assistant.data.worker.PortraitPrefetchWorker] has successfully
 * downloaded + optimized every hero's `hero.main/pick/ban.png` set, so subsequent app
 * launches skip straight past the prefetch worker instead of re-scanning the roster.
 *
 * Uses [com.mlbb.assistant.appDataStore] — the single authoritative DataStore extension
 * property. Never declare a second `preferencesDataStore` delegate for the same file name.
 */
object PortraitPrefetchPreference {

    private val PREFETCH_DONE = booleanPreferencesKey("portrait_prefetch_done")

    suspend fun isDone(context: Context): Boolean =
        context.appDataStore.data.first()[PREFETCH_DONE] ?: false

    suspend fun setDone(context: Context, done: Boolean) {
        context.appDataStore.edit { prefs -> prefs[PREFETCH_DONE] = done }
    }
}
