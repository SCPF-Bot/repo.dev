package com.mlbb.assistant

import android.app.Application
import com.mlbb.assistant.data.local.crashlog.AppLogTree
import com.mlbb.assistant.data.local.crashlog.installCrashHandler
import com.mlbb.assistant.presentation.overlay.DraftOverlayContent
import com.mlbb.assistant.presentation.overlay.JetOverlay
import com.mlbb.assistant.utils.DevModeManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Root application class.
 *
 * Calls [JetOverlay.initialize] to register [DraftOverlayContent] as the overlay
 * composable. JetOverlay initialises its internal WindowManager state here so that
 * [com.mlbb.assistant.presentation.overlay.OverlayService] only needs to call
 * [JetOverlay.show] / [JetOverlay.hide] to control visibility.
 */
@HiltAndroidApp
class MLBBApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Install crash handler before anything else so even early crashes are captured.
        installCrashHandler(applicationContext)

        // Default Timber tree for debug builds.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Persist WARN/ERROR/CRASH to the internal log file (all builds).
        Timber.plant(AppLogTree(applicationContext))

        // Restore the DevLogAlias component-enabled state after installs / OTA updates,
        // which reset component state to the manifest default (enabled).
        DevModeManager.applyStoredState(applicationContext)

        JetOverlay.initialize(this) {
            overlayContent { DraftOverlayContent() }
        }
    }
}
