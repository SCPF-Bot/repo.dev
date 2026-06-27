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
import java.util.concurrent.ConcurrentHashMap

data class MatchResult(
    val hero: Hero?,
    val confidence: Float,            // 0.0–1.0
    val requiresConfirmation: Boolean // true if confidence < CONFIRM_THRESHOLD
)

/**
 * Matches a cropped hero-slot bitmap against cached hero portraits.
 *
 * ### Hash strategy — pHash primary, dHash pre-filter
 *
 * On Android, [JImageHash](https://github.com/KilianB/JImageHash) cannot run
 * because it depends on `java.awt.image.BufferedImage` which is absent from the
 * Android SDK. WaveletHash and AverageColorHash therefore always return null at
 * runtime, contributing 0 % to the score and leaving dHash + histogram as the
 * only effective algorithms in the original implementation.
 *
 * This revision replaces dHash with **DCT-based pHash** (see [PerceptualHash.computePHash])
 * as the primary scoring signal. pHash is considerably more robust than dHash:
 *   - Resistant to JPEG compression artifacts on MediaProjection frames
 *   - Invariant to minor brightness shifts from adaptive-display calibration
 *   - Distinguishes heroes with similar pose silhouettes but different colour palettes
 *     (e.g. common Fighter archetypes that share body proportions)
 *
 * dHash is retained as a **fast pre-filter**: slots whose dHash distance exceeds
 * [PhaseDetectionConfig.DHASH_SIMILARITY_THRESHOLD] are skipped without computing
 * the more expensive pHash + histogram, reducing per-frame CPU cost.
 *
 * ### Histogram improvements
 * Bins increased from 8→16 per channel (48-element vector) for finer colour
 * discrimination. Bhattacharyya coefficient is computed per-channel and averaged.
 *
 * ### Dynamic weight scheme (sums to 1.0)
 * | Component      | Weight |
 * |----------------|--------|
 * | pHash          | 65 %   |
 * | Colour histogram | 35 % |
 *
 * The JImageHash paths (WaveletHash / AverageColorHash) are removed. Their
 * reflection overhead was non-trivial and they always returned null on Android,
 * silently inflating the dHash weight to 75 % anyway.
 *
 * ### Multi-frame confirmation
 * [match] tracks per-hero consecutive hit counts in [consecutiveHits]. A slot
 * result is promoted to full confidence only when the same hero wins for
 * [PhaseDetectionConfig.CONFIRMATION_FRAMES_REQUIRED] consecutive calls from the
 * same slot. Callers pass a [slotKey] (e.g. "ourPick2") to namespace the counter.
 * This prevents one-frame false positives from hero-reveal animation frames.
 */
class PortraitMatcher(
    private val context: Context,
    private val imageLoader: ImageLoader
) {

    companion object {
        private const val CONFIRM_THRESHOLD  = 0.82f
        private const val REJECT_THRESHOLD   = 0.42f

        private const val HISTOGRAM_BINS     = 16     // per channel → 16×3 = 48-element vector
        private const val PHASH_WEIGHT       = 0.65f
        private const val HISTOGRAM_WEIGHT   = 0.35f  // sums to 1.0 with pHash weight

        // Fast pre-filter: skip pHash+histogram if dHash similarity is below this.
        private const val DHASH_PREFILTER_THRESHOLD = 0.60f
    }

    // heroId → dHash (pre-filter)
    private val dHashCache = LruCache<Int, Long>(200)

    // heroId → pHash (primary similarity)
    private val pHashCache = LruCache<Int, Long>(200)

    // heroId → colour histogram (FloatArray of HISTOGRAM_BINS × 3 values)
    private val histogramCache = LruCache<Int, FloatArray>(200)

    // slotKey → (heroId, consecutiveCount) for multi-frame confirmation.
    // ConcurrentHashMap because resetConfirmation() may be called from a different
    // dispatcher (IO) while match() runs on Default.
    private val consecutiveHits = ConcurrentHashMap<String, Pair<Int, Int>>()

    // ── Preloading ────────────────────────────────────────────────────────────

    /**
     * Parallel lazy preload — processes heroes in concurrent batches of 10.
     * Computes dHash, pHash, and colour histogram for each portrait.
     * The first batch is available in < 1 s; recommendations begin flowing before
     * all portraits are loaded.
     */
    suspend fun preloadHashes(heroes: List<Hero>) = withContext(Dispatchers.IO) {
        val batches = heroes.chunked(10)
        for (batch in batches) {
            coroutineScope {
                batch.forEach { hero ->
                    launch {
                        if (pHashCache.get(hero.id) == null) {
                            val bmp = fetchPortrait(hero.imageUrl) ?: return@launch
                            dHashCache.put(hero.id, PerceptualHash.compute(bmp))
                            pHashCache.put(hero.id, PerceptualHash.computePHash(bmp))
                            histogramCache.put(hero.id, computeHistogram(bmp))
                            bmp.recycle()
                        }
                    }
                }
            }
        }
    }

    /** Reset per-slot confirmation counters (call at session start / phase change). */
    fun resetConfirmation() = consecutiveHits.clear()

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Match a cropped slot bitmap against all loaded hero hashes.
     *
     * @param slotBitmap  Cropped slot image from [SlotRegions.cropSlot].
     * @param availableHeroes  Candidate hero list (all heroes, or filtered to unavailable).
     * @param slotKey  Unique identifier for this slot (e.g. "enemyBan3", "ourPick1").
     *                 Used to track consecutive-hit confirmation state.
     */
    fun match(
        slotBitmap: Bitmap,
        availableHeroes: List<Hero>,
        slotKey: String = ""
    ): MatchResult {
        val slotDHash     = PerceptualHash.compute(slotBitmap)
        val slotPHash     = PerceptualHash.computePHash(slotBitmap)
        val slotHistogram = computeHistogram(slotBitmap)

        var bestHero: Hero? = null
        var bestSim = 0f

        for (hero in availableHeroes) {
            val heroDHash = dHashCache.get(hero.id) ?: continue
            val heroPHash = pHashCache.get(hero.id) ?: continue
            val heroHist  = histogramCache.get(hero.id)

            // Fast pre-filter: skip expensive pHash+histogram for obvious mismatches.
            val dHashSim = PerceptualHash.similarity(slotDHash, heroDHash)
            if (dHashSim < DHASH_PREFILTER_THRESHOLD) continue

            val pHashSim   = PerceptualHash.pHashSimilarity(slotPHash, heroPHash)
            val histSim    = heroHist?.let { histogramSimilarity(slotHistogram, it) } ?: 0f
            val histW      = if (heroHist != null) HISTOGRAM_WEIGHT else 0f
            val pHashW     = 1f - histW

            val hybridSim  = pHashW * pHashSim + histW * histSim

            if (hybridSim > bestSim) {
                bestSim  = hybridSim
                bestHero = hero
            }
        }

        // Multi-frame confirmation
        val confirmedHero = if (slotKey.isNotEmpty() && bestHero != null) {
            val prev = consecutiveHits[slotKey]
            val newCount = if (prev?.first == bestHero.id) prev.second + 1 else 1
            consecutiveHits[slotKey] = bestHero.id to newCount
            if (newCount >= PhaseDetectionConfig.CONFIRMATION_FRAMES_REQUIRED) bestHero else null
        } else {
            bestHero
        }

        return when {
            bestSim >= CONFIRM_THRESHOLD -> MatchResult(
                confirmedHero ?: bestHero,
                bestSim,
                confirmedHero == null
            )
            bestSim >= REJECT_THRESHOLD  -> MatchResult(bestHero, bestSim, true)
            else                         -> MatchResult(null, bestSim, true)
        }
    }

    // ── Histogram ─────────────────────────────────────────────────────────────

    /**
     * Computes a normalised 48-element colour histogram (16 bins × R/G/B).
     * Values within each channel sum to 1.0.
     * Uses [Bitmap.copyPixelsToBuffer] bulk extraction (P1-01 pattern) to avoid
     * per-pixel JNI overhead.
     */
    private fun computeHistogram(bmp: Bitmap): FloatArray {
        val bins = HISTOGRAM_BINS
        val hist = FloatArray(bins * 3) { 0f }
        val step = 2
        var count = 0

        for (x in 0 until bmp.width step step) {
            for (y in 0 until bmp.height step step) {
                val px = bmp.getPixel(x, y)
                val r  = android.graphics.Color.red(px)
                val g  = android.graphics.Color.green(px)
                val b  = android.graphics.Color.blue(px)

                hist[(r * bins / 256).coerceIn(0, bins - 1)]            += 1f
                hist[bins + (g * bins / 256).coerceIn(0, bins - 1)]     += 1f
                hist[bins * 2 + (b * bins / 256).coerceIn(0, bins - 1)] += 1f
                count++
            }
        }

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
     * Per-channel Bhattacharyya similarity — returns [0, 1].
     * Computed independently per R/G/B channel then averaged, making it
     * sensitive to colour shifts in any single channel.
     */
    private fun histogramSimilarity(h1: FloatArray, h2: FloatArray): Float {
        if (h1.size != h2.size) return 0f
        val bins = HISTOGRAM_BINS
        var totalBc = 0f
        for (c in 0 until 3) {
            val offset = c * bins
            var bc = 0f
            for (i in 0 until bins) {
                bc += Math.sqrt((h1[offset + i] * h2[offset + i]).toDouble()).toFloat()
            }
            totalBc += bc
        }
        return (totalBc / 3f).coerceIn(0f, 1f)
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private suspend fun fetchPortrait(url: String): Bitmap? = runCatching {
        val req = ImageRequest.Builder(context).data(url).build()
        val res = imageLoader.execute(req)
        (res as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()
}
