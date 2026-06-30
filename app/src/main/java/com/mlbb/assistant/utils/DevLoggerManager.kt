package com.mlbb.assistant.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import timber.log.Timber

/**
 * Manages the optional third-party verbose logger companion app.
 *
 * The companion app ([LOGGER_PACKAGE]) captures full logcat output — including
 * output produced before or during an app crash — which is not possible from
 * inside the crashing process itself.  This makes it especially useful when the
 * app crashes instantly on launch before [CrashLogStore] has a chance to write.
 *
 * Behaviour:
 * - [promptInstallIfNeeded] checks whether the logger is already installed.
 *   If not, it opens the Play Store listing and records that the prompt was shown
 *   so subsequent launches do not re-prompt.
 * - [isInstalled] returns `true` when the logger is already present on the device.
 * - [launch] opens the logger directly if installed, otherwise falls back to
 *   [promptInstallIfNeeded].
 *
 * The "prompted" flag is stored in [SharedPreferences] (not DataStore) so it is
 * readable synchronously without a coroutine context — required for the early
 * [MainActivity.onCreate] call site.
 */
object DevLoggerManager {

    const val LOGGER_PACKAGE = "com.dp.logcatapp"
    private const val PREFS_NAME = "mlbb_dev_prefs"
    private const val KEY_LOGGER_PROMPTED = "dev_logger_prompted"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(LOGGER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Shows the Play Store listing for the logger app if it is not yet installed
     * and the prompt has not been shown before.
     *
     * Call this from [MainActivity.onCreate] on fresh installs (developer mode
     * defaults to ON) and whenever the developer toggle is turned ON in Settings.
     *
     * @param force When `true`, skips the "already prompted" guard. Use this when
     *              the user explicitly toggles developer mode ON.
     */
    fun promptInstallIfNeeded(context: Context, force: Boolean = false) {
        if (isInstalled(context)) {
            Timber.d("DevLoggerManager: logger already installed, skipping prompt")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyPrompted = prefs.getBoolean(KEY_LOGGER_PROMPTED, false)
        if (alreadyPrompted && !force) {
            Timber.d("DevLoggerManager: install already prompted, skipping")
            return
        }

        prefs.edit().putBoolean(KEY_LOGGER_PROMPTED, true).apply()
        openPlayStore(context)
    }

    /**
     * Opens the logger app if installed; otherwise prompts for installation.
     */
    fun launch(context: Context) {
        if (isInstalled(context)) {
            runCatching {
                val intent = context.packageManager
                    .getLaunchIntentForPackage(LOGGER_PACKAGE)
                    ?: return@runCatching
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.onFailure { Timber.w(it, "DevLoggerManager: failed to launch logger app") }
        } else {
            promptInstallIfNeeded(context, force = true)
        }
    }

    private fun openPlayStore(context: Context) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$LOGGER_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val webFallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$LOGGER_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            context.startActivity(marketIntent)
        }.onFailure {
            runCatching {
                context.startActivity(webFallback)
            }.onFailure { e ->
                Timber.e(e, "DevLoggerManager: could not open Play Store for logger install")
            }
        }
    }
}
