package com.mlbb.assistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mlbb.assistant.data.local.crashlog.AppLogTree
import com.mlbb.assistant.data.local.crashlog.installCrashHandler
import com.mlbb.assistant.data.worker.HeroSyncWorker
import com.mlbb.assistant.data.worker.PortraitPrefetchWorker
import com.mlbb.assistant.presentation.overlay.DraftOverlayContent
import com.mlbb.assistant.presentation.overlay.JetOverlay
import com.mlbb.assistant.utils.DevModeManager
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
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory]
 * for dependency injection into [@HiltWorker][androidx.hilt.work.HiltWorker]-
 * annotated workers.
 */
@HiltAndroidApp
class MLBBApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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

        scheduleHeroSync()
        schedulePortraitPrefetch()
    }

    // ── WorkManager configuration ─────────────────────────────────────────────

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

    // ── First-launch hero portrait prefetch ───────────────────────────────────

    /**
     * Enqueues [PortraitPrefetchWorker] on every cold start. The worker itself checks
     * [com.mlbb.assistant.data.local.preferences.PortraitPrefetchPreference] and no-ops
     * immediately once the roster has been fully downloaded + optimized once, so this is
     * cheap on all launches after the first successful one. [ExistingWorkPolicy.KEEP] avoids
     * queuing a duplicate run if a previous attempt is still pending/retrying.
     */
    private fun schedulePortraitPrefetch() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PortraitPrefetchWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        val wm = try {
            WorkManager.getInstance(this)
        } catch (e: IllegalStateException) {
            WorkManager.initialize(this, workManagerConfiguration)
            WorkManager.getInstance(this)
        }

        wm.enqueueUniqueWork(
            PortraitPrefetchWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
