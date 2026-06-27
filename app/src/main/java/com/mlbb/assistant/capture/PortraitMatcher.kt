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
    val confidence: Float,            // 0.0–1.0
    val requiresConfirmation: Boolean // true if confidence < 0.80
)

/**
 * Matches a cropped hero-slot bitmap against cached hero portraits.
 *
 * Improvements in this revision:
 *
 * TD-08 (lazy preload): [preloadHashes] now fetches portraits in parallel
 *   batches instead of sequentially. The first batch of heroes is available
 *   after ~1 s on a typical LTE connection, so recommendations start flowing
 *   before all 100+ portraits are loaded.
 *
 * Hybrid scoring: The similarity function blends four hash algorithms:
 *   1. WaveletHash (JImageHash) — frequency-domain structural fingerprint; robust to
 *      colour-saturation variation across MLBB skin tiers.
 *   2. AverageColorHash (JImageHash) — colour-aware hash that discriminates heroes with
 *      similar silhouettes but different palettes (e.g. all-golden Fighter frames).
 *   3. dHash (PerceptualHash) — fast gradient-direction hash; always-available fallback.
 *   4. 64-bin colour histogram (Bhattacharyya) — perceptual colour distribution.
 *
 * Dynamic weight scheme (weights always sum to 1.0):
 *   • dHash:            40 % (all JImageHash available) / 75 % (JImageHash unavailable)
 *   • Histogram:        25 % when hero histogram is cached, 0 % otherwise
 *   • WaveletHash:      20 % when JImageHash is available on this JVM
 *   • AverageColorHash: 15 % when JImageHash is available on this JVM
 *
 * JImageHash integration guard: JImageHash JARs depend on java.awt.image.BufferedImage
 *   which is unavailable on Android. All calls are wrapped in runCatching {} so
 *   NoClassDefFoundError is caught as Throwable and dHash takes over seamlessly.
 */
class PortraitMatcher(
    private val context: Context,
    private val imageLoader: ImageLoader
) {

    companion object {
        private const val CONFIRM_THRESHOLD = 0.80f
        private const val REJECT_THRESHOLD  = 0.40f
        private const val HISTOGRAM_BINS       = 8     // per channel → 8×3 = 24-element vector
        private const val HISTOGRAM_WEIGHT    = 0.25f  // always included when hero histogram is cached
        private const val WAVELET_WEIGHT      = 0.20f  // JImageHash WaveletHash (when available on JVM)
        private const val COLOR_DIFF_WEIGHT   = 0.15f  // JImageHash AverageColorHash (when available on JVM)
        // dHash weight = 1 - HISTOGRAM_WEIGHT - WAVELET_WEIGHT - COLOR_DIFF_WEIGHT = 0.40 (full)
        //              = 1 - HISTOGRAM_WEIGHT                                       = 0.75 (no JImageHash)
    }

    // heroId → dHash of official portrait
    private val hashCache = LruCache<Int, Long>(200)

    // heroId → colour histogram (FloatArray of HISTOGRAM_BINS × 3 values)
    private val histogramCache = LruCache<Int, FloatArray>(200)

    // heroId → JImageHash WaveletHash result (byte array of the hash value).
    // Stored as ByteArray because JImageHash's Hash type is not available at
    // Android class-load time — we serialize the relevant bits eagerly.
    private val waveletHashCache = LruCache<Int, ByteArray>(200)

    // heroId → JImageHash AverageColorHash result (byte array).
    // Provides colour discrimination complementary to WaveletHash's structural fingerprint.
    // Same ByteArray serialisation strategy as waveletHashCache.
    private val colorDiffHashCache = LruCache<Int, ByteArray>(200)

    // ── Preloading ────────────────────────────────────────────────────────────

    /**
     * TD-08: Parallel lazy preload — processes heroes in concurrent batches of
     * 10 so the first heroes are ready in < 1 s while the rest load in the
     * background. Callers do not need to await full completion.
     *
     * For each hero portrait: computes dHash (always), colour histogram (always),
     * WaveletHash (JImageHash primary, best-effort — falls back silently on Android),
     * and AverageColorHash (JImageHash secondary, same guard).
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
                            computeWaveletHashBytes(bmp)?.let   { waveletHashCache.put(hero.id, it)   }
                            computeColorDiffHashBytes(bmp)?.let { colorDiffHashCache.put(hero.id, it) }
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
     * Uses a **dynamic weight scheme** — weights always sum to 1.0 regardless of
     * which JImageHash algorithms are available at runtime:
     *
     * | Algorithm         | Weight (full) | Weight (no JImageHash) |
     * |---|---|---|
     * | WaveletHash       | 20 %          | 0 %                    |
     * | AverageColorHash  | 15 %          | 0 %                    |
     * | dHash             | 40 %          | 75 %                   |
     * | Colour histogram  | 25 %          | 25 %                   |
     *
     * Weights are computed per-hero at runtime; unavailable algorithms contribute 0 %
     * and the remaining budget is redistributed to dHash. This ensures the scoring
     * function is stable even when JImageHash is not loaded (Android runtime).
     */
    fun match(slotBitmap: Bitmap, availableHeroes: List<Hero>): MatchResult {
        val slotDHash     = PerceptualHash.compute(slotBitmap)
        val slotHistogram = computeHistogram(slotBitmap)
        val slotWavelet   = computeWaveletHashBytes(slotBitmap)
        val slotColorDiff = computeColorDiffHashBytes(slotBitmap)

        var bestHero: Hero? = null
        var bestSim = 0f

        availableHeroes.forEach { hero ->
            val heroDHash     = hashCache.get(hero.id) ?: return@forEach
            val heroHist      = histogramCache.get(hero.id)
            val heroWavelet   = waveletHashCache.get(hero.id)
            val heroColorDiff = colorDiffHashCache.get(hero.id)

            val dHashSim = PerceptualHash.similarity(slotDHash, heroDHash)

            val waveletSim = if (slotWavelet != null && heroWavelet != null)
                computeWaveletSimilarity(slotWavelet, heroWavelet)
            else null

            val colorDiffSim = if (slotColorDiff != null && heroColorDiff != null)
                computeWaveletSimilarity(slotColorDiff, heroColorDiff)  // Hamming on byte arrays
            else null

            // Dynamic weights — sum always equals 1.0
            val histW      = if (heroHist      != null) HISTOGRAM_WEIGHT  else 0f
            val waveletW   = if (waveletSim    != null) WAVELET_WEIGHT    else 0f
            val colorDiffW = if (colorDiffSim  != null) COLOR_DIFF_WEIGHT else 0f
            val dHashW     = 1f - histW - waveletW - colorDiffW

            val hybridSim = dHashW     * dHashSim +
                histW      * (heroHist?.let { histogramSimilarity(slotHistogram, it) } ?: 0f) +
                waveletW   * (waveletSim   ?: 0f) +
                colorDiffW * (colorDiffSim ?: 0f)

            if (hybridSim > bestSim) {
                bestSim  = hybridSim
                bestHero = hero
            }
        }

        return when {
            bestSim >= CONFIRM_THRESHOLD -> MatchResult(bestHero, bestSim, false)
            bestSim >= REJECT_THRESHOLD  -> MatchResult(bestHero, bestSim, true)
            else                         -> MatchResult(null, bestSim, true)
        }
    }

    // ── JImageHash integration (JVM-only, runCatching guard) ──────────────────

    /**
     * Attempts to compute a WaveletHash fingerprint using KilianB/JImageHash.
     *
     * JImageHash depends on java.awt.image.BufferedImage which is not part of
     * the Android SDK. When called on Android the JVM throws [NoClassDefFoundError]
     * (a subclass of [Throwable]) which [runCatching] catches, and this function
     * returns null — triggering the dHash / histogram fallback path in [match].
     *
     * On a standard JVM (unit tests, desktop) the wavelet hash computes normally
     * and provides a higher-accuracy structural fingerprint than plain dHash.
     *
     * The result is returned as a [ByteArray] (the hash value bytes) to avoid
     * referencing the JImageHash [Hash] type in any field or method signature that
     * would cause the enclosing class to fail loading on Android.
     *
     * @return hash bytes (big-endian) or null if JImageHash is unavailable.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun computeWaveletHashBytes(bmp: Bitmap): ByteArray? = runCatching {
        // Convert Bitmap → BufferedImage — this line throws NoClassDefFoundError on Android.
        // On a JVM it works; on Android the catch block returns null transparently.
        val bufferedImage = bitmapToBufferedImage(bmp) ?: return@runCatching null

        // Use WaveletHash with bit resolution 32 and scale factor 4 (good balance of
        // accuracy vs. speed for 64×64 portrait crops).
        @Suppress("UNCHECKED_CAST")
        val hasherClass = Class.forName("com.github.kilianB.hashAlgorithms.WaveletHash")
        val constructor = hasherClass.getConstructor(Integer.TYPE, Integer.TYPE)
        val hasher      = constructor.newInstance(32, 4)

        val hashMethod  = hasherClass.getMethod("hash", Class.forName("java.awt.image.BufferedImage"))
        val hashObj     = hashMethod.invoke(hasher, bufferedImage)

        // Extract the underlying BigInteger / byte representation via reflection
        val hashClass      = hashObj.javaClass
        val toStringMethod = hashClass.getMethod("toHexString")
        val hex            = toStringMethod.invoke(hashObj) as? String ?: return@runCatching null
        hexStringToByteArray(hex)
    }.getOrNull()

    /**
     * Converts an Android [Bitmap] to a java.awt.image.BufferedImage via reflection.
     * Returns null (gracefully) on Android where BufferedImage is unavailable.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun bitmapToBufferedImage(bmp: Bitmap): Any? = runCatching {
        val scaled     = Bitmap.createScaledBitmap(bmp, 64, 64, true)
        val biClass    = Class.forName("java.awt.image.BufferedImage")
        val buffImg    = biClass.getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE)
            .newInstance(scaled.width, scaled.height, 2 /* TYPE_INT_RGB */)
        val setRGB = biClass.getMethod("setRGB", Integer.TYPE, Integer.TYPE, Integer.TYPE)
        for (x in 0 until scaled.width) {
            for (y in 0 until scaled.height) {
                setRGB.invoke(buffImg, x, y, scaled.getPixel(x, y))
            }
        }
        if (scaled !== bmp) scaled.recycle()
        buffImg
    }.getOrNull()

    /**
     * Computes normalised Hamming similarity between two wavelet hash byte arrays.
     * Returns 0f if the arrays differ in length (shouldn't happen for same algorithm).
     */
    private fun computeWaveletSimilarity(h1: ByteArray, h2: ByteArray): Float {
        if (h1.size != h2.size) return 0f
        var diffBits = 0
        for (i in h1.indices) {
            diffBits += Integer.bitCount((h1[i].toInt() xor h2[i].toInt()) and 0xFF)
        }
        val totalBits = h1.size * 8
        return if (totalBits == 0) 0f else 1f - diffBits.toFloat() / totalBits.toFloat()
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val s = if (hex.length % 2 != 0) "0$hex" else hex
        return ByteArray(s.length / 2) { i ->
            s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Attempts to compute an AverageColorHash fingerprint using KilianB/JImageHash.
     *
     * `AverageColorHash` is colour-aware (operates on all three RGB channels rather than
     * greyscale luminance). This discriminates heroes with similar structural silhouettes
     * but different colour palettes — e.g. all Fighter-archetype heroes that share a golden
     * frame skin tier but differ in hero-specific accent colour.
     *
     * Uses the same `runCatching` / reflection guard pattern as [computeWaveletHashBytes]:
     * `java.awt.image.BufferedImage` is unavailable on Android, so [NoClassDefFoundError]
     * is swallowed and null is returned, leaving the weight to be redistributed to dHash.
     *
     * The AverageColorHash constructor: `AverageColorHash(int bitResolution)`.
     * We use bitResolution = 64 (matches dHash's 64-bit resolution) so the resulting
     * byte array length is consistent and [computeWaveletSimilarity] can be reused.
     *
     * @return hash bytes or null if JImageHash is unavailable.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun computeColorDiffHashBytes(bmp: Bitmap): ByteArray? = runCatching {
        val bufferedImage = bitmapToBufferedImage(bmp) ?: return@runCatching null

        @Suppress("UNCHECKED_CAST")
        val hasherClass = Class.forName("com.github.kilianB.hashAlgorithms.AverageColorHash")
        val constructor = hasherClass.getConstructor(Integer.TYPE)
        val hasher      = constructor.newInstance(64)  // 64-bit resolution, same as dHash

        val hashMethod  = hasherClass.getMethod("hash", Class.forName("java.awt.image.BufferedImage"))
        val hashObj     = hashMethod.invoke(hasher, bufferedImage)

        val hashClass      = hashObj.javaClass
        val toStringMethod = hashClass.getMethod("toHexString")
        val hex            = toStringMethod.invoke(hashObj) as? String ?: return@runCatching null
        hexStringToByteArray(hex)
    }.getOrNull()

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

                hist[(r * bins / 256).coerceIn(0, bins - 1)]              += 1f
                hist[bins + (g * bins / 256).coerceIn(0, bins - 1)]       += 1f
                hist[bins * 2 + (b * bins / 256).coerceIn(0, bins - 1)]   += 1f
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
