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

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN
            )
            .build()

    // ── Periodic hero data sync ───────────────────────────────────────────────

    /**
     * Schedules [HeroSyncWorker] to run every 24 hours on a network connection.
     * [ExistingPeriodicWorkPolicy.KEEP] preserves existing scheduled requests
     * so repeated app launches do not reset the countdown.
     */
    private fun scheduleHeroSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<HeroSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HeroSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
