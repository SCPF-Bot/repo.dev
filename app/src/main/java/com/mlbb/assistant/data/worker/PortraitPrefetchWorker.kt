package com.mlbb.assistant.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mlbb.assistant.R
import com.mlbb.assistant.data.local.preferences.PortraitPrefetchPreference
import com.mlbb.assistant.data.portrait.PortraitAssetManager
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import com.mlbb.assistant.domain.usecase.SyncHeroesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * One-time, first-launch hero-portrait prefetch.
 *
 * Downloads every hero's CDN portrait ([PortraitAssetManager.Variant.MAIN]) and derives
 * the two smaller slot-detection variants ([PortraitAssetManager.Variant.PICK] /
 * [PortraitAssetManager.Variant.BAN]) in the background, so the on-screen draft detector
 * and hero grids already have local assets ready the first time the user opens Settings
 * or starts a draft — without them having to manually tap Download/Optimize first.
 *
 * Scheduled unconditionally (as unique work with [androidx.work.ExistingWorkPolicy.KEEP])
 * from [com.mlbb.assistant.MLBBApplication] on every cold start. [PortraitPrefetchPreference]
 * short-circuits the work immediately once it has completed successfully, so this is a
 * cheap no-op on all launches after the first.
 *
 * **Retry policy:** mirrors [HeroSyncWorker] — up to 3 attempts with WorkManager's built-in
 * exponential back-off before giving up for this launch. Because the prefetch-done flag is
 * only set on success, a fresh attempt is automatically re-enqueued (via KEEP) the next time
 * the app cold-starts, so a temporarily offline first launch is not a permanent miss.
 *
 * **Network constraint:** the [androidx.work.Constraints] applied at scheduling time require
 * a connected network, matching [PortraitAssetManager.downloadMain]'s reliance on the CDN.
 */
@HiltWorker
class PortraitPrefetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val heroRepository: HeroRepository,
    private val syncHeroesUseCase: SyncHeroesUseCase,
    private val portraitAssetManager: PortraitAssetManager,
) : CoroutineWorker(context, params) {

    /**
     * Promotes this worker to a foreground service so Android grants it full network
     * access. Plain background workers are killed or network-restricted on many vendor
     * ROMs (MIUI, HyperOS, ColorOS, OxygenOS) — exactly the devices MLBB players use.
     * Running as a foreground service matches the same network priority the old version
     * relied on via [com.mlbb.assistant.presentation.overlay.OverlayService].
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "Portrait Download",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading hero portraits…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result {
        if (PortraitPrefetchPreference.isDone(applicationContext)) {
            return Result.success()
        }

        // Elevate to foreground service before any network calls so Android grants
        // full network access regardless of battery optimisation / vendor ROM policy.
        setForeground(getForegroundInfo())

        Timber.d("PortraitPrefetchWorker: starting first-launch portrait prefetch (attempt ${runAttemptCount + 1})")
        return try {
            val heroes = loadHeroes()
            if (heroes.isEmpty()) {
                Timber.w("PortraitPrefetchWorker: no heroes available yet, will retry")
                return retryOrFail()
            }

            portraitAssetManager.downloadAll(heroes)
            portraitAssetManager.optimizeAll(heroes)
            PortraitPrefetchPreference.setDone(applicationContext, true)

            Timber.i("PortraitPrefetchWorker: prefetched ${heroes.size} hero portrait sets")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "PortraitPrefetchWorker: prefetch failed on attempt ${runAttemptCount + 1}")
            retryOrFail()
        }
    }

    /** Reads the current hero roster, triggering a sync first if the local cache is empty. */
    private suspend fun loadHeroes(): List<Hero> {
        val cached = heroRepository.getHeroes().first()
        if (cached.isNotEmpty()) return cached

        syncHeroesUseCase()
        return heroRepository.getHeroes().first()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        /** Unique name used with [androidx.work.ExistingWorkPolicy.KEEP]. */
        const val WORK_NAME = "PortraitPrefetchWorker"

        /** Maximum retry attempts before marking the work as permanently failed for this launch. */
        private const val MAX_RETRY_ATTEMPTS = 3

        private const val NOTIF_CHANNEL = "portrait_prefetch_channel"
        private const val NOTIF_ID      = 2
    }
}
