package com.mlbb.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mlbb.assistant.data.local.crashlog.AppLogTree
import com.mlbb.assistant.data.local.crashlog.installCrashHandler
import com.mlbb.assistant.data.worker.HeroSyncWorker
import com.mlbb.assistant.presentation.overlay.DraftOverlayContent
import com.mlbb.assistant.presentation.overlay.JetOverlay
import com.pluto.Pluto
import com.pluto.plugins.datastore.pref.PlutoDatastorePreferencesPlugin
import com.pluto.plugins.exceptions.PlutoExceptionsPlugin
import com.pluto.plugins.logger.PlutoLoggerPlugin
import com.pluto.plugins.logger.PlutoTimberTree
import com.pluto.plugins.network.PlutoNetworkPlugin
import com.pluto.plugins.preferences.PlutoSharePreferencesPlugin
import com.pluto.plugins.rooms.db.PlutoRoomsDatabasePlugin
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Root application class.
 *
 * Calls [JetOverlay.initialize] to register [DraftOverlayContent] as the overlay
 * composable. JetOverlay initialises its internal WindowManager state here so that
 * [com.mlbb.assistant.presentation.overlay.OverlayService] only needs to call
 * [JetOverlay.show] / [JetOverlay.hide] to control visibility.
 *
 * Note: The overlayContent lambda is registered here but EXECUTED only when
 * [JetOverlay.show] runs from [OverlayService.onCreate], at which point
 * [com.mlbb.assistant.presentation.overlay.OverlayContentBridge] is already
 * populated with the Hilt-injected dependencies.
 *
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory]
 * for dependency injection into [@HiltWorker][androidx.hilt.work.HiltWorker]-
 * annotated workers.  Do not declare the WorkManager startup initializer in
 * AndroidManifest.xml alongside this provider — the two paths conflict.
 */
@HiltAndroidApp
class MLBBApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        installCrashHandler(applicationContext)

        // ── Pluto: embedded debug companion ─────────────────────────────────────
        // Installs a persistent floating bubble (debug builds only). Tap it to open
        // the inspector panel — log viewer, network calls, crash reports, Room DB
        // browser, DataStore viewer, and SharedPreferences viewer. No Play Store or
        // Google Play Services dependency. Release builds use no-op stubs.
        if (BuildConfig.DEBUG) {
            Pluto.Installer(this)
                .addPlugin(PlutoExceptionsPlugin())
                .addPlugin(PlutoLoggerPlugin())
                .addPlugin(PlutoNetworkPlugin())
                .addPlugin(PlutoRoomsDatabasePlugin())
                .addPlugin(PlutoDatastorePreferencesPlugin())
                .addPlugin(PlutoSharePreferencesPlugin())
                .install()
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            // Route all Timber output into the Pluto log viewer panel so every
            // tag/level is visible in the bubble inspector without Logcat.
            Timber.plant(PlutoTimberTree())
        }
        Timber.plant(AppLogTree(applicationContext))

        // Register DraftOverlayContent with JetOverlay BEFORE OverlayService starts.
        // OverlayService.onCreate() calls JetOverlay.show() which executes this lambda.
        // OverlayContentBridge will be populated by that point so state reads are safe.
        JetOverlay.initialize(this) {
            overlayContent { DraftOverlayContent() }
        }

        scheduleHeroSync()
    }

    // ── WorkManager configuration ─────────────────────────────────────────────

    /**
     * P0 fix: guard against the ContentProvider-based AndroidX Startup initialization
     * race where [workManagerConfiguration] is called BEFORE [super.onCreate] injects
     * [workerFactory]. Using [isInitialized] avoids [UninitializedPropertyAccessException]
     * if the getter is invoked early; WorkManager will fall back to default factory in
     * that window (workers requiring injection won't run, but the app won't crash).
     *
     * The correct factory is always present by the time [scheduleHeroSync] runs because
     * [scheduleHeroSync] is called after [super.onCreate] completes Hilt field injection.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .apply {
                if (::workerFactory.isInitialized) setWorkerFactory(workerFactory)
            }
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN
            )
            .build()

    // ── Periodic hero data sync ───────────────────────────────────────────────

    /**
     * Schedules [HeroSyncWorker] to run every 24 hours on a network connection.
     * [ExistingPeriodicWorkPolicy.KEEP] preserves existing scheduled requests
     * so repeated app launches do not reset the countdown.
     *
     * P0 fix: wrapped in try/catch so an [IllegalStateException] from a double-
     * initialization path (seen on certain OEM ROMs) does not crash the app at launch.
     * If WorkManager is somehow already initialized by a system-level path, we simply
     * obtain the existing instance instead of re-initializing.
     */
    private fun scheduleHeroSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<HeroSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        val wm = try {
            WorkManager.getInstance(this)
        } catch (e: IllegalStateException) {
            WorkManager.initialize(this, workManagerConfiguration)
            WorkManager.getInstance(this)
        }

        wm.enqueueUniquePeriodicWork(
            HeroSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
