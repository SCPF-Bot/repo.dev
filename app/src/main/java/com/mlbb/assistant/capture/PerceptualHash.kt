package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Difference hash (dHash) – fast perceptual image comparison.
 * Produces a 64-bit hash; Hamming distance < 10 → same image.
 */
object PerceptualHash {

    private const val HASH_SIZE = 8   // 8×8 grid → 64-bit hash

    /** Compute dHash of a bitmap. Returns Long (bit-packed). */
    fun compute(bmp: Bitmap): Long {
        // Scale to (HASH_SIZE+1) × HASH_SIZE
        val scaled = Bitmap.createScaledBitmap(bmp, HASH_SIZE + 1, HASH_SIZE, false)

        var hash = 0L
        var bit  = 0
        for (y in 0 until HASH_SIZE) {
            for (x in 0 until HASH_SIZE) {
                val left  = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        scaled.recycle()
        return hash
    }

    /** Hamming distance between two hashes. */
    fun distance(a: Long, b: Long): Int =
        java.lang.Long.bitCount(a xor b)

    /** Similarity 0.0–1.0 (1.0 = identical). */
    fun similarity(a: Long, b: Long): Float =
        1f - distance(a, b) / 64f

    /** True if the two hashes represent the same image (threshold = 10/64). */
    fun isSame(a: Long, b: Long, threshold: Int = 10): Boolean =
        distance(a, b) <= threshold

    private fun luminance(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
