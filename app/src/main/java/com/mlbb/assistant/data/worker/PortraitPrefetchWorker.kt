package com.mlbb.assistant.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

    override suspend fun doWork(): Result {
        if (PortraitPrefetchPreference.isDone(applicationContext)) {
            return Result.success()
        }

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
    }
}
