package com.mlbb.assistant.capture

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Section 3.5 — Unit tests for the perceptual hash (dHash) component of
 * [PortraitMatcher].
 *
 * These tests exercise the hash distance calculation in isolation using
 * synthetic bitmaps, avoiding filesystem access.
 *
 * Requires Robolectric for [Bitmap] creation outside of Android runtime.
 * The tests are annotated @Config(manifest = Config.NONE) so they run
 * without a full AndroidManifest (unit-test source set only).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PerceptualHashTest {

    // ── dHash helpers (mirrors PortraitMatcher implementation) ────────────────

    /**
     * Compute a 64-bit dHash from [bmp].
     * The bitmap is resized to 9×8 and each bit indicates whether the pixel
     * is brighter than its right neighbour.
     */
    private fun dHash(bmp: Bitmap): Long {
        val resized = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left  = resized.getPixel(x,     y)
                val right = resized.getPixel(x + 1, y)
                val lum   = { px: Int ->
                    (0.299 * android.graphics.Color.red(px) +
                     0.587 * android.graphics.Color.green(px) +
                     0.114 * android.graphics.Color.blue(px))
                }
                if (lum(left) > lum(right)) hash = hash or (1L shl (y * 8 + x))
            }
        }
        resized.recycle()
        return hash
    }

    /** Hamming distance between two 64-bit hashes. */
    private fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    // ── identical images ──────────────────────────────────────────────────────

    @Test
    fun identicalBitmapsHaveZeroHashDistance() {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.argb(255, 120, 80, 60))
        val h1 = dHash(bmp)
        val h2 = dHash(bmp)
        bmp.recycle()
        assertEquals(0, hammingDistance(h1, h2))
    }

    // ── solid fill bitmaps ────────────────────────────────────────────────────

    @Test
    fun solidBlackHasAllZeroBits() {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.BLACK)
        val h = dHash(bmp)
        bmp.recycle()
        assertEquals(0L, h)
    }

    @Test
    fun solidWhiteHasAllZeroBits() {
        // All adjacent pixels equal → no bit set
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        val h = dHash(bmp)
        bmp.recycle()
        assertEquals(0L, h)
    }

    // ── gradient bitmap ───────────────────────────────────────────────────────

    @Test
    fun leftToRightGradientSetsAllBitsInRow() {
        // A left-darker, right-brighter gradient means every left<right comparison
        // yields false (left NOT brighter) → all bits 0.
        // A left-brighter gradient sets all bits.
        val bmp = Bitmap.createBitmap(32, 8, Bitmap.Config.ARGB_8888)
        for (x in 0 until 32) {
            // Brightness decreases left to right (left is always brighter)
            val lum = (255 - x * 8).coerceAtLeast(0)
            for (y in 0 until 8) {
                bmp.setPixel(x, y, android.graphics.Color.rgb(lum, lum, lum))
            }
        }
        val h = dHash(bmp)
        bmp.recycle()
        // All 64 bits should be set
        assertEquals(64, java.lang.Long.bitCount(h))
    }

    // ── similar images ────────────────────────────────────────────────────────

    @Test
    fun slightlyModifiedBitmapHasLowHashDistance() {
        val bmp1 = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bmp1.eraseColor(android.graphics.Color.argb(255, 100, 150, 200))

        val bmp2 = bmp1.copy(Bitmap.Config.ARGB_8888, true)
        // Flip one pixel — should not change hash much
        bmp2.setPixel(0, 0, android.graphics.Color.argb(255, 99, 149, 199))

        val dist = hammingDistance(dHash(bmp1), dHash(bmp2))
        bmp1.recycle(); bmp2.recycle()
        assertTrue("Expected distance < 5, was $dist", dist < 5)
    }

    // ── dissimilar images ─────────────────────────────────────────────────────

    @Test
    fun totallyDifferentBitmapsHaveHighHashDistance() {
        val dark = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        dark.eraseColor(android.graphics.Color.BLACK)

        // Build a white-with-black-stripes bitmap for maximum dHash divergence
        val striped = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (x in 0 until 32) {
            val col = if (x % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            for (y in 0 until 32) striped.setPixel(x, y, col)
        }

        val dist = hammingDistance(dHash(dark), dHash(striped))
        dark.recycle(); striped.recycle()
        assertTrue("Expected distance > 20, was $dist", dist > 20)
    }

    // ── Hamming distance arithmetic ───────────────────────────────────────────

    @Test
    fun hammingDistanceOfZeroXorIsZero() {
        assertEquals(0, hammingDistance(0xDEADBEEFL, 0xDEADBEEFL))
    }

    @Test
    fun hammingDistanceOfAllOnesXorIsMaxBits() {
        val bits = hammingDistance(-1L, 0L)  // -1L = all 1s
        assertEquals(64, bits)
    }
}
