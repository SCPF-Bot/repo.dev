package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Slot type classification used to tune matching behaviour per UI region
 * (recommendations.md §3 — "SlotType-aware" detection).
 *
 * Ban slots and pick slots render hero portraits with different occlusions:
 *  - BAN slots overlay a diagonal red "banned" slash across the portrait.
 *  - PICK slots overlay a bottom name strip and, for our team, a small rank/role badge.
 *  - CENTER is the large hover/lock-in preview — no persistent occlusion, but is
 *    subject to animation frames (fly-in, glow) so it is treated conservatively.
 */
enum class SlotType {
    BAN,
    PICK,
    CENTER
}

/**
 * Normalizes cropped slot bitmaps before hashing so that UI chrome specific to a
 * [SlotType] doesn't get baked into the perceptual signature (recommendations.md §5.1).
 *
 * All operations are pure bitmap math — no OpenCV / java.awt dependency, so this
 * runs safely on-device.
 */
object PortraitNormalizer {

    /** Working resolution all hashers operate on. Keeping this fixed makes hash bits comparable. */
    const val NORMALIZED_SIZE = 128

    /**
     * Scales [source] to [NORMALIZED_SIZE]×[NORMALIZED_SIZE] and masks out the
     * occlusion regions specific to [slotType], then equalizes luminance so
     * brightness/gamma differences across devices don't skew the hash.
     *
     * Caller owns [source] and is responsible for recycling it; the returned
     * bitmap is always a new instance that the caller must recycle.
     */
    fun normalizeForSlot(source: Bitmap, slotType: SlotType): Bitmap {
        val scaled = Bitmap.createScaledBitmap(source, NORMALIZED_SIZE, NORMALIZED_SIZE, true)
        val masked = maskOcclusion(scaled, slotType)
        if (masked !== scaled) scaled.recycle()
        val equalized = equalizeLuminance(masked)
        if (equalized !== masked) masked.recycle()
        return equalized
    }

    /**
     * Blacks out the pixel regions known to contain slot-type-specific chrome:
     *  - BAN: diagonal red slash band across the middle of the portrait.
     *  - PICK: bottom name strip.
     *  - CENTER: no masking — full portrait is meaningful.
     *
     * Uses bulk [Bitmap.setPixels] writes, avoiding per-pixel JNI overhead.
     */
    private fun maskOcclusion(bitmap: Bitmap, slotType: SlotType): Bitmap {
        val size = NORMALIZED_SIZE
        return when (slotType) {
            SlotType.BAN -> {
                val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                // Diagonal ban slash sits roughly across the vertical center third of
                // the portrait — mask a horizontal band there rather than trying to
                // trace the exact diagonal, which is cheap and robust to slash-angle drift.
                val bandTop = (size * 0.42f).toInt()
                val bandBottom = (size * 0.58f).toInt()
                val bandH = (bandBottom - bandTop).coerceAtLeast(1)
                val zeros = IntArray(size * bandH) { Color.BLACK }
                result.setPixels(zeros, 0, size, 0, bandTop, size, bandH)
                result
            }
            SlotType.PICK -> {
                val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                // Bottom hero-name strip (~12% of height).
                val stripH = (size * 0.12f).toInt().coerceAtLeast(1)
                val stripY = size - stripH
                val zeros = IntArray(size * stripH) { Color.BLACK }
                result.setPixels(zeros, 0, size, 0, stripY, size, stripH)
                result
            }
            SlotType.CENTER -> bitmap
        }
    }

    /**
     * Lightweight CLAHE-equivalent luminance equalization — stretches the histogram
     * to the full [0,255] range without any OpenCV dependency (recommendations.md §5.1).
     */
    private fun equalizeLuminance(bitmap: Bitmap): Bitmap {
        val size = NORMALIZED_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        var minLum = 255
        var maxLum = 0
        for (p in pixels) {
            val lum = ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val range = (maxLum - minLum).coerceAtLeast(1)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val stretched = IntArray(pixels.size) { i ->
            val a = pixels[i] ushr 24
            val r = (((pixels[i] shr 16 and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
            val g = (((pixels[i] shr 8 and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
            val b = (((pixels[i] and 0xFF) - minLum) * 255 / range).coerceIn(0, 255)
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        result.setPixels(stretched, 0, size, 0, 0, size, size)
        return result
    }
}
