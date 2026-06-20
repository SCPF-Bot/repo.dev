package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.mlbb.assistant.domain.engine.Rank

/**
 * Section 3.2.1 — Rank auto-detection from screen region colour.
 *
 * MLBB displays a coloured rank emblem badge on the draft lobby screen.
 * We sample a small region at the top of the frame where the rank badge
 * typically appears and classify the dominant hue into a [Rank] bucket.
 *
 * Hue ranges (HSV, degrees 0–360):
 *   IMMORTAL       ≈ 25–40   (amber / gold-orange)
 *   MYTHICAL_GLORY ≈ 35–55   (gold-yellow, brighter)
 *   MYTHICAL_HONOR ≈ 30–50   (gold, slightly cooler)
 *   MYTHIC         ≈ 270–300 (violet-purple)
 *   LEGEND         ≈ 190–230 (cyan-blue)
 *   EPIC           ≈ 240–270 (mid blue-purple)
 *
 * These are soft heuristics; exact values vary across devices and aspect
 * ratios.  The engine falls back to [Rank.UNKNOWN] when confidence is low.
 */
object RankDetector {

    /**
     * Region (relative to frame dimensions) where the rank badge appears.
     * Coordinates are in [0,1] normalised space.
     */
    private val RANK_REGION = SlotRegionF(left = 0.02f, top = 0.02f, right = 0.18f, bottom = 0.10f)

    data class DetectionResult(val rank: Rank, val confidence: Float)

    /**
     * Detects the rank from [frame].
     * Returns a [DetectionResult] with [Rank.UNKNOWN] and confidence 0 if
     * the region cannot be classified.
     */
    fun detect(frame: Bitmap): DetectionResult {
        val crop = runCatching { SlotRegions.cropSlot(frame, RANK_REGION) }.getOrNull()
            ?: return DetectionResult(Rank.UNKNOWN, 0f)

        return try {
            val dominant = dominantHsv(crop)
            classify(dominant)
        } finally {
            crop.recycle()
        }
    }

    /**
     * Returns the HSV triple [hue, saturation, value] of the most-sampled
     * colour bucket in [bmp].  Pixels with low saturation (< 0.25) are
     * excluded to ignore greyscale UI chrome.
     */
    private fun dominantHsv(bmp: Bitmap): FloatArray {
        val step = 4
        val hsv  = FloatArray(3)

        // Accumulate hue votes in 36 buckets of 10° each.
        val hueBuckets = IntArray(36)
        var satSum = 0f
        var valSum = 0f
        var count  = 0

        for (x in 0 until bmp.width step step) {
            for (y in 0 until bmp.height step step) {
                Color.colorToHSV(bmp.getPixel(x, y), hsv)
                if (hsv[1] < 0.25f) continue  // skip near-grey pixels
                hueBuckets[(hsv[0] / 10f).toInt().coerceIn(0, 35)]++
                satSum += hsv[1]
                valSum += hsv[2]
                count++
            }
        }

        if (count == 0) return floatArrayOf(0f, 0f, 0f)

        val dominantBucket = hueBuckets.indices.maxByOrNull { hueBuckets[it] } ?: 0
        return floatArrayOf(
            dominantBucket * 10f + 5f,  // bucket centre hue
            satSum / count,
            valSum / count
        )
    }

    /**
     * Maps dominant HSV hue + value to a [Rank] bucket with a confidence score.
     */
    private fun classify(hsv: FloatArray): DetectionResult {
        val hue  = hsv[0]
        val sat  = hsv[1]
        val `val` = hsv[2]

        if (sat < 0.20f || `val` < 0.20f) {
            return DetectionResult(Rank.UNKNOWN, 0f)
        }

        return when {
            // Immortal: amber-orange (25–40°), high value
            hue in 25f..40f && `val` > 0.7f ->
                DetectionResult(Rank.IMMORTAL, 0.75f)

            // Mythical Glory: gold-yellow (40–60°)
            hue in 40f..60f && `val` > 0.6f ->
                DetectionResult(Rank.MYTHICAL_GLORY, 0.70f)

            // Mythical Honor: warm gold (30–45°), moderate value
            hue in 30f..45f ->
                DetectionResult(Rank.MYTHICAL_HONOR, 0.60f)

            // Mythic: violet-purple (260–310°)
            hue in 260f..310f ->
                DetectionResult(Rank.MYTHIC, 0.70f)

            // Legend: cyan-blue (185–235°)
            hue in 185f..235f ->
                DetectionResult(Rank.LEGEND, 0.70f)

            // Epic: blue-purple (235–265°)
            hue in 235f..265f ->
                DetectionResult(Rank.EPIC, 0.65f)

            else -> DetectionResult(Rank.UNKNOWN, 0f)
        }
    }
}
