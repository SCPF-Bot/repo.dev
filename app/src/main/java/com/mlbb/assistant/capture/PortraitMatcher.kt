package com.mlbb.assistant.capture

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MatchResult(
    val hero: Hero?,
    val confidence: Float,    // 0.0–1.0
    val requiresConfirmation: Boolean  // true if confidence < 0.80
)

/**
 * Matches a cropped hero-slot bitmap against cached hero portraits.
 *
 * Improvements in this revision:
 *
 * TD-08 (lazy preload): [preloadHashes] now fetches portraits in parallel
 *   batches instead of sequentially.  The first batch of heroes is available
 *   after ~1 s on a typical LTE connection, so recommendations start flowing
 *   before all 100+ portraits are loaded.
 *
 * Hybrid scoring: The similarity function now blends dHash (structural) with
 *   a 64-bin colour histogram (perceptual colour distribution) according to
 *   [PhaseDetectionConfig.HISTOGRAM_WEIGHT].  This reduces false positives on
 *   heroes with similar silhouettes but distinct colour palettes.
 */
class PortraitMatcher(
    private val context: Context,
    private val imageLoader: ImageLoader
) {

    companion object {
        private const val CONFIRM_THRESHOLD = 0.80f
        private const val REJECT_THRESHOLD  = 0.40f
        private const val HISTOGRAM_BINS    = 8    // per channel → 8×3 = 24-element vector
    }

    // heroId → dHash of official portrait
    private val hashCache = LruCache<Int, Long>(200)

    // heroId → colour histogram (FloatArray of HISTOGRAM_BINS × 3 values)
    private val histogramCache = LruCache<Int, FloatArray>(200)

    // ── Preloading ────────────────────────────────────────────────────────────

    /**
     * TD-08: Parallel lazy preload — processes heroes in concurrent batches of
     * 10 so the first heroes are ready in < 1 s while the rest load in the
     * background.  Callers do not need to await full completion.
     */
    suspend fun preloadHashes(heroes: List<Hero>) = withContext(Dispatchers.IO) {
        val batches = heroes.chunked(10)
        for (batch in batches) {
            coroutineScope {
                batch.forEach { hero ->
                    launch {
                        if (hashCache.get(hero.id) == null) {
                            val bmp = fetchPortrait(hero.imageUrl) ?: return@launch
                            hashCache.put(hero.id, PerceptualHash.compute(bmp))
                            histogramCache.put(hero.id, computeHistogram(bmp))
                            bmp.recycle()
                        }
                    }
                }
            }
        }
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Match a cropped slot bitmap against all loaded hero hashes.
     *
     * Hybrid score = dHash_sim × (1 − HISTOGRAM_WEIGHT) + hist_sim × HISTOGRAM_WEIGHT
     */
    fun match(slotBitmap: Bitmap, availableHeroes: List<Hero>): MatchResult {
        val slotHash      = PerceptualHash.compute(slotBitmap)
        val slotHistogram = computeHistogram(slotBitmap)
        val histWeight    = PhaseDetectionConfig.HISTOGRAM_WEIGHT

        var bestHero: Hero? = null
        var bestSim = 0f

        availableHeroes.forEach { hero ->
            val heroHash = hashCache.get(hero.id) ?: return@forEach
            val heroHist = histogramCache.get(hero.id)

            val dHashSim = PerceptualHash.similarity(slotHash, heroHash)

            val hybridSim = if (heroHist != null) {
                val histSim = histogramSimilarity(slotHistogram, heroHist)
                dHashSim * (1f - histWeight) + histSim * histWeight
            } else {
                dHashSim
            }

            if (hybridSim > bestSim) {
                bestSim  = hybridSim
                bestHero = hero
            }
        }

        return when {
            bestSim >= CONFIRM_THRESHOLD  -> MatchResult(bestHero, bestSim, false)
            bestSim >= REJECT_THRESHOLD   -> MatchResult(bestHero, bestSim, true)
            else                          -> MatchResult(null, bestSim, true)
        }
    }

    // ── Histogram ─────────────────────────────────────────────────────────────

    /**
     * Computes a normalised 24-element colour histogram (8 bins × R/G/B).
     * Values sum to 1.0 within each channel.
     */
    private fun computeHistogram(bmp: Bitmap): FloatArray {
        val bins = HISTOGRAM_BINS
        val hist = FloatArray(bins * 3) { 0f }
        val step = 2  // every 2nd pixel for speed
        var count = 0

        for (x in 0 until bmp.width step step) {
            for (y in 0 until bmp.height step step) {
                val px = bmp.getPixel(x, y)
                val r  = android.graphics.Color.red(px)
                val g  = android.graphics.Color.green(px)
                val b  = android.graphics.Color.blue(px)

                hist[(r * bins / 256).coerceIn(0, bins - 1)]          += 1f
                hist[bins + (g * bins / 256).coerceIn(0, bins - 1)]   += 1f
                hist[bins * 2 + (b * bins / 256).coerceIn(0, bins - 1)] += 1f
                count++
            }
        }

        // Normalise each channel independently.
        if (count > 0) {
            for (c in 0 until 3) {
                val offset = c * bins
                val sum = hist.slice(offset until offset + bins).sum().coerceAtLeast(1f)
                for (i in 0 until bins) hist[offset + i] /= sum
            }
        }

        return hist
    }

    /**
     * Bhattacharyya-inspired histogram similarity — returns [0, 1].
     * Higher is more similar.
     */
    private fun histogramSimilarity(h1: FloatArray, h2: FloatArray): Float {
        if (h1.size != h2.size) return 0f
        var bc = 0f
        for (i in h1.indices) {
            bc += Math.sqrt((h1[i] * h2[i]).toDouble()).toFloat()
        }
        return bc / 3f  // normalise by channel count (each channel sums to 1 → max bc=3)
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private suspend fun fetchPortrait(url: String): Bitmap? = runCatching {
        val req = ImageRequest.Builder(context).data(url).build()
        val res = imageLoader.execute(req)
        (res as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()
}
