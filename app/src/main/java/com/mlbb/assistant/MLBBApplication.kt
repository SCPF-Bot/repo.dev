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
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Root application class.
 *
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory]
 * for dependency injection into workers annotated with [@HiltWorker][HiltWorker].
 * This replaces the default WorkManager initialization — **do not** declare
 * `<provider androidx.startup.InitializationProvider>` with the WorkManager
 * initializer in AndroidManifest.xml, as Hilt's manual configuration takes
 * precedence and the two initialisation paths conflict.
 *
 * Scheduled work:
 * - [HeroSyncWorker] runs every 24 hours when the device is connected to a network.
 *   Policy is [ExistingPeriodicWorkPolicy.KEEP] — an existing scheduled request is
 *   never replaced on subsequent app launches.
 */
@HiltAndroidApp
class MLBBApplication : Application(), Configuration.Provider {

    /**
     * Injected by Hilt. Must be `lateinit` because Hilt injection runs after
     * [Application] construction but before [onCreate].
     */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Install the global crash-to-file handler first so even crashes during
        // startup are captured before the process dies.
        installCrashHandler(applicationContext)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Always plant the persistent log tree so WARN/ERROR entries are saved
        // regardless of build type. The tree writes synchronously to avoid
        // losing entries when the process is terminated immediately after a crash.
        Timber.plant(AppLogTree(applicationContext))

        scheduleHeroSync()
    }

    // ── WorkManager configuration (Hilt integration) ──────────────────────────

    /**
     * Provides the WorkManager configuration that uses [HiltWorkerFactory]
     * so worker classes annotated with [@HiltWorker][HiltWorker] receive
     * injected dependencies correctly.
     *
     * Note: WorkManager's automatic initialisation via [androidx.startup] must
     * be disabled (or removed from the manifest) when this provider is present.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    // ── Periodic background sync scheduling ───────────────────────────────────

    /**
     * Schedules [HeroSyncWorker] to run every 24 hours when the device is
     * connected to a network.
     *
     * [ExistingPeriodicWorkPolicy.KEEP] ensures subsequent app launches do not
     * restart the 24-hour countdown — the existing scheduled request is preserved.
     *
     * Addresses: recommendations.md §7.1 (WorkManager periodic hero data sync);
     * `todo.md §10` (OSS library adoption queue — WorkManager + HeroSyncWorker).
     */
    private fun scheduleHeroSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<HeroSyncWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HeroSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
