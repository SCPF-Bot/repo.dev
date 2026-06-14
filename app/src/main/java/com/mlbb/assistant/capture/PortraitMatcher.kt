package com.mlbb.assistant.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MatchResult(
    val hero: Hero?,
    val confidence: Float,    // 0.0–1.0
    val requiresConfirmation: Boolean  // true if confidence < 0.80
)

class PortraitMatcher(
    private val context: Context,
    private val imageLoader: ImageLoader
) {

    companion object {
        private const val CONFIRM_THRESHOLD = 0.80f
        private const val REJECT_THRESHOLD  = 0.40f
    }

    // heroId → perceptual hash of official portrait
    private val hashCache = LruCache<Int, Long>(200)

    /** Pre-load hashes for all heroes. Call once on init. */
    suspend fun preloadHashes(heroes: List<Hero>) = withContext(Dispatchers.IO) {
        heroes.forEach { hero ->
            if (hashCache.get(hero.id) == null) {
                val bmp = fetchPortrait(hero.imageUrl) ?: return@forEach
                hashCache.put(hero.id, PerceptualHash.compute(bmp))
                bmp.recycle()
            }
        }
    }

    /**
     * Match a cropped slot bitmap against all loaded hero hashes.
     * Returns the best match with confidence score.
     */
    fun match(slotBitmap: Bitmap, availableHeroes: List<Hero>): MatchResult {
        val slotHash = PerceptualHash.compute(slotBitmap)
        var bestHero: Hero? = null
        var bestSim = 0f

        availableHeroes.forEach { hero ->
            val heroHash = hashCache.get(hero.id) ?: return@forEach
            val sim = PerceptualHash.similarity(slotHash, heroHash)
            if (sim > bestSim) {
                bestSim = sim
                bestHero = hero
            }
        }

        return when {
            bestSim >= CONFIRM_THRESHOLD  -> MatchResult(bestHero, bestSim, false)
            bestSim >= REJECT_THRESHOLD   -> MatchResult(bestHero, bestSim, true)
            else                          -> MatchResult(null, bestSim, true)
        }
    }

    private suspend fun fetchPortrait(url: String): Bitmap? = runCatching {
        val req = ImageRequest.Builder(context).data(url).build()
        val res = imageLoader.execute(req)
        (res as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()
}
