package com.mlbb.assistant.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Manages the Developer mode toggle that controls whether the [LogViewerActivity]
 * appears as a second launcher icon in the app drawer.
 *
 * Visibility is controlled via [PackageManager.setComponentEnabledSetting] applied
 * to the `DevLogAlias` activity-alias declared in AndroidManifest.xml.
 *
 * The on/off state is persisted in [SharedPreferences] (not DataStore) so it can
 * be read synchronously in [com.mlbb.assistant.MLBBApplication.onCreate] without
 * a coroutine context — required to restore the alias state before the launcher
 * queries the package manager.
 *
 * Default is ON: a fresh install automatically shows the log viewer icon.
 */
object DevModeManager {

    private const val PREFS_NAME    = "mlbb_dev_prefs"
    private const val KEY_DEV_MODE  = "developer_mode"

    /**
     * Fully-qualified class name of the activity-alias declared in the manifest.
     * Must match `android:name` on `<activity-alias>` (expanded with namespace).
     */
    private const val ALIAS_CLASS = "com.mlbb.assistant.DevLogAlias"

    // ── Public API ────────────────────────────────────────────────────────────

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEV_MODE, true)

    /**
     * Persists [enabled] and immediately applies the change to the package manager
     * so the launcher icon appears or disappears without a restart.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEV_MODE, enabled).apply()
        applyToPackageManager(context, enabled)
    }

    /**
     * Reads the stored preference and applies it to the package manager.
     * Call once in [Application.onCreate] so the correct icon state is restored
     * after an OTA update (which resets component-enabled state to manifest default).
     */
    fun applyStoredState(context: Context) = applyToPackageManager(context, isEnabled(context))

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun applyToPackageManager(context: Context, enabled: Boolean) {
        val state = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, ALIAS_CLASS),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
