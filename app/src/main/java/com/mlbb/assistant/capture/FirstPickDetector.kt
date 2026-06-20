package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Section 3.2.2 — First-pick side auto-detection.
 *
 * MLBB's draft lobby highlights the team that has first pick with a
 * bright accent colour on the left (our team) or right (enemy team)
 * panel.  We sample small indicator regions at the top-left and
 * top-right corners of the frame and compare their mean luminance.
 *
 * The side with the brighter indicator is assumed to be the first picker.
 * If both sides have similar luminance the result is UNKNOWN.
 */
object FirstPickDetector {

    enum class FirstPick { OUR_TEAM, ENEMY_TEAM, UNKNOWN }

    data class DetectionResult(val firstPick: FirstPick, val confidence: Float)

    /** Left-team indicator panel (normalised coordinates). */
    private val LEFT_PANEL  = SlotRegionF(left = 0.01f, top = 0.04f, right = 0.12f, bottom = 0.09f)

    /** Right-team indicator panel. */
    private val RIGHT_PANEL = SlotRegionF(left = 0.88f, top = 0.04f, right = 0.99f, bottom = 0.09f)

    /**
     * Minimum luminance difference (0–255) between the two panels for a
     * confident classification.
     */
    private const val MIN_DIFF = 20f

    fun detect(frame: Bitmap): DetectionResult {
        val leftLum  = meanLuminance(frame, LEFT_PANEL)
        val rightLum = meanLuminance(frame, RIGHT_PANEL)
        val diff     = kotlin.math.abs(leftLum - rightLum)

        if (diff < MIN_DIFF) return DetectionResult(FirstPick.UNKNOWN, 0f)

        val confidence = (diff / 80f).coerceIn(0f, 1f)
        return if (leftLum > rightLum) {
            // Our team (left panel) is brighter — we pick first.
            DetectionResult(FirstPick.OUR_TEAM, confidence)
        } else {
            // Enemy team (right panel) is brighter — enemy picks first.
            DetectionResult(FirstPick.ENEMY_TEAM, confidence)
        }
    }

    private fun meanLuminance(frame: Bitmap, region: SlotRegionF): Float {
        val crop = runCatching { SlotRegions.cropSlot(frame, region) }.getOrNull()
            ?: return 0f
        return try {
            var total = 0f
            var count = 0
            val step  = 3
            for (x in 0 until crop.width  step step) {
                for (y in 0 until crop.height step step) {
                    val px = crop.getPixel(x, y)
                    total += 0.299f * Color.red(px) +
                             0.587f * Color.green(px) +
                             0.114f * Color.blue(px)
                    count++
                }
            }
            if (count > 0) total / count else 0f
        } finally {
            crop.recycle()
        }
    }
}
