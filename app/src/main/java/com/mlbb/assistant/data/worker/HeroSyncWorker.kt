package com.mlbb.assistant.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * TD-13 — WorkManager periodic hero-data sync.
 *
 * Runs [SyncHeroesUseCase] in a guaranteed background job that survives
 * process death and OS restarts. Scheduled as a 24-hour periodic work
 * request in [com.mlbb.assistant.MLBBApplication] with
 * [androidx.work.ExistingPeriodicWorkPolicy.KEEP] — only one instance
 * runs at a time, and an already-scheduled request is never replaced.
 *
 * **Retry policy:** on any exception, retries up to 3 times with
 * WorkManager's built-in exponential back-off before marking the work
 * as [androidx.work.ListenableWorker.Result.failure]. The stale local
 * seed data (`default_heroes.json`) serves as an offline fallback so a
 * single failed sync does not degrade the app experience.
 *
 * **Network constraint:** the [androidx.work.Constraints] applied at
 * scheduling time require a connected network, so the worker never
 * runs when offline, preserving battery and preventing spurious failures.
 */
@HiltWorker
class HeroSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncHeroesUseCase: SyncHeroesUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("HeroSyncWorker: starting periodic hero data sync (attempt ${runAttemptCount + 1})")
        return try {
            syncHeroesUseCase()
            Timber.i("HeroSyncWorker: sync completed successfully")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Rethrow CancellationException so WorkManager can cancel the worker
            // cooperatively. Swallowing it prevents the coroutine from terminating
            // cleanly when the work is cancelled by the OS or by WorkManager itself.
            throw e
        } catch (e: Exception) {
            Timber.w(e, "HeroSyncWorker: sync failed on attempt ${runAttemptCount + 1}")
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        /** Unique name used with [androidx.work.ExistingPeriodicWorkPolicy.KEEP]. */
        const val WORK_NAME = "HeroSyncWorker"

        /** Maximum retry attempts before marking the work as permanently failed. */
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
