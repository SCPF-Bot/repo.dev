package com.mlbb.assistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single authoritative DataStore extension property for the whole app.
 *
 * `preferencesDataStore` creates ONE singleton DataStore per Context object.
 * It must only be declared ONCE in the entire codebase — multiple declarations
 * for the same file name cause an IllegalStateException at runtime.
 *
 * All callers (Hilt modules, WizardPreference, etc.) must use THIS property.
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "mlbb_preferences")
