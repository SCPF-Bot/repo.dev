package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos

/**
 * Perceptual image hashing — two algorithms for different accuracy/speed trade-offs.
 *
 * ### dHash (difference hash) — fast, 64-bit
 * Scales to 9×8, compares adjacent pixel luminance gradients.
 * Good enough for quick pre-filtering; used at REJECT_THRESHOLD stage.
 * Hamming distance < 10 → very likely the same image.
 *
 * ### pHash (DCT perceptual hash) — accurate, 64-bit
 * Scales to 32×32, applies 2D Discrete Cosine Transform, retains the top-left
 * 8×8 low-frequency coefficients (excluding DC). This representation is robust to:
 *   - JPEG compression artifacts on screenshots
 *   - Minor gamma/brightness shifts between devices (adaptive brightness)
 *   - Sub-pixel scale differences across screen resolutions
 *   - Skin tier colour overlays (same structural hash despite different tints)
 *
 * pHash is the primary hash used by [PortraitMatcher]; dHash is retained as the
 * fast pre-filter and for the `isSame` threshold check path.
 *
 * Reference: Zauner (2010) "Implementation and Benchmarking of Perceptual Image Hash Functions"
 * https://www.phash.org/docs/pubs/thesis_zauner.pdf
 */
object PerceptualHash {

    // ── dHash ─────────────────────────────────────────────────────────────────

    private const val DHASH_SIZE = 8   // 8×8 grid → 64-bit hash

    /** Compute dHash of a bitmap. Returns Long (bit-packed). */
    fun compute(bmp: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bmp, DHASH_SIZE + 1, DHASH_SIZE, false)
        var hash = 0L
        var bit  = 0
        for (y in 0 until DHASH_SIZE) {
            for (x in 0 until DHASH_SIZE) {
                val left  = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        scaled.recycle()
        return hash
    }

    /** Hamming distance between two dHash values. */
    fun distance(a: Long, b: Long): Int =
        java.lang.Long.bitCount(a xor b)

    /** Similarity 0.0–1.0 (1.0 = identical). */
    fun similarity(a: Long, b: Long): Float =
        1f - distance(a, b) / 64f

    /** True if the two hashes represent the same image (threshold = 10/64). */
    fun isSame(a: Long, b: Long, threshold: Int = 10): Boolean =
        distance(a, b) <= threshold

    // ── pHash (DCT-based) ─────────────────────────────────────────────────────

    /**
     * Size of the intermediate DCT image (32×32 px after greyscale scaling).
     * Higher values improve accuracy but increase CPU cost quadratically.
     * 32×32 is the standard used by the phash.org reference implementation.
     */
    private const val PHASH_SIZE     = 32

    /**
     * Number of low-frequency DCT coefficients retained per axis.
     * We keep the top-left 8×8 block (excluding the DC term at [0,0]).
     * This gives 8×8 − 1 = 63 bits, packed into a 64-bit Long (MSB unused).
     */
    private const val PHASH_KEEP     = 8

    /**
     * Pre-computed DCT cosine table: dctTable[k][n] = cos(π·k·(2n+1) / (2·N))
     * where N = PHASH_SIZE. Computed once at class-load time.
     */
    private val dctTable: Array<FloatArray> = Array(PHASH_KEEP) { k ->
        FloatArray(PHASH_SIZE) { n ->
            cos(Math.PI * k * (2.0 * n + 1.0) / (2.0 * PHASH_SIZE)).toFloat()
        }
    }

    /**
     * Computes a 64-bit DCT perceptual hash (pHash) for [bmp].
     *
     * Steps:
     *  1. Scale to 32×32 greyscale.
     *  2. Apply separable 2D DCT, retain 8×8 low-frequency block.
     *  3. Compute mean of the 63 AC coefficients (skip DC at [0,0]).
     *  4. Pack: bit[i] = 1 if coefficient > mean, else 0.
     *
     * @return 64-bit pHash (MSB is unused / always 0).
     */
    fun computePHash(bmp: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bmp, PHASH_SIZE, PHASH_SIZE, true)

        // Extract greyscale luminance grid
        val grey = FloatArray(PHASH_SIZE * PHASH_SIZE)
        for (y in 0 until PHASH_SIZE) {
            for (x in 0 until PHASH_SIZE) {
                grey[y * PHASH_SIZE + x] = luminance(scaled.getPixel(x, y))
            }
        }
        scaled.recycle()

        // Separable 2D DCT: rows first, then columns
        val dct = Array(PHASH_KEEP) { FloatArray(PHASH_KEEP) }

        // Row-pass: for each kept frequency v (0..PHASH_KEEP-1), project each row
        val rowDct = Array(PHASH_SIZE) { y ->
            FloatArray(PHASH_KEEP) { k ->
                var sum = 0f
                for (x in 0 until PHASH_SIZE) sum += grey[y * PHASH_SIZE + x] * dctTable[k][x]
                sum
            }
        }

        // Column-pass: for each kept (u, v), project across rows
        for (u in 0 until PHASH_KEEP) {
            for (v in 0 until PHASH_KEEP) {
                var sum = 0f
                for (y in 0 until PHASH_SIZE) sum += rowDct[y][v] * dctTable[u][y]
                dct[u][v] = sum
            }
        }

        // Compute mean of AC coefficients (skip DC at [0][0])
        var acSum = 0f
        var acCount = 0
        for (u in 0 until PHASH_KEEP) {
            for (v in 0 until PHASH_KEEP) {
                if (u == 0 && v == 0) continue
                acSum += dct[u][v]
                acCount++
            }
        }
        val mean = if (acCount > 0) acSum / acCount else 0f

        // Pack bits
        var hash = 0L
        var bit  = 0
        for (u in 0 until PHASH_KEEP) {
            for (v in 0 until PHASH_KEEP) {
                if (u == 0 && v == 0) continue
                if (dct[u][v] > mean) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /**
     * pHash similarity 0.0–1.0 (1.0 = identical).
     * Uses the same 63 usable bits as [computePHash]; bit 63 is always 0.
     */
    fun pHashSimilarity(a: Long, b: Long): Float =
        1f - java.lang.Long.bitCount(a xor b) / 63f

    // ── Shared utilities ──────────────────────────────────────────────────────

    /** ITU-R BT.601 luminance from an ARGB pixel. */
    fun luminance(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
