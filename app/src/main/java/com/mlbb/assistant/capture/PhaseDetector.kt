package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.mlbb.assistant.domain.engine.DraftPhase
import java.util.ArrayDeque

/**
 * Detects the current MLBB draft phase from a screen frame.
 *
 * ### Detection strategy — two layers, priority order:
 *
 * **Layer 1 — HSV colour analysis (primary)**
 * Converts each sampled pixel to HSV. The MLBB action-button region has a
 * very specific hue in each phase:
 *   - BAN:  red hue  (0–[PhaseDetectionConfig.BAN_HUE_LOW]° or
 *           >[PhaseDetectionConfig.BAN_HUE_HIGH]°), high saturation.
 *   - PICK: teal/blue hue ([PhaseDetectionConfig.PICK_HUE_LOW]–
 *           [PhaseDetectionConfig.PICK_HUE_HIGH]°), high saturation.
 * HSV is more robust than raw-RGB ratios because it separates hue from
 * brightness, making the detector resilient to adaptive-brightness displays,
 * HDR screens, and MLBB skin-tier tint shifts.
 *
 * **Layer 2 — RGB ratio fallback**
 * Retained from the original implementation for devices where HSV sampling
 * produces low-confidence results (very small action-button crops, extreme
 * display colour profiles).
 *
 * **Phase history smoothing**
 * A circular history of size [PhaseDetectionConfig.PHASE_HISTORY_SIZE] smooths
 * noisy single-frame detections. A phase is only declared when it is the
 * majority verdict in the history window. This prevents the overlay from
 * flickering between BAN and UNKNOWN during hero-lock animations.
 *
 * All magic-number thresholds live in [PhaseDetectionConfig] (TD-03).
 */
object PhaseDetector {

    data class PhaseResult(
        val phase: DetectedPhase,
        val confidence: Float     // 0.0 – 1.0
    )

    enum class DetectedPhase {
        SETUP,          // "Hero Ban Phase will start in X" visible
        BAN,            // Red button region dominant
        PICK,           // Blue/green button region dominant
        TRADING,        // Swap buttons visible
        LOADING,        // Loading bar detected
        UNKNOWN
    }

    // ── Phase history (smoothing) ─────────────────────────────────────────────

    private val history = ArrayDeque<DetectedPhase>(PhaseDetectionConfig.PHASE_HISTORY_SIZE)

    /**
     * Majority vote over the current [history] window.
     * Returns UNKNOWN if the window is not yet full.
     */
    private fun historicalMajority(): DetectedPhase {
        if (history.size < PhaseDetectionConfig.PHASE_HISTORY_SIZE) return DetectedPhase.UNKNOWN
        val counts = history.groupingBy { it }.eachCount()
        val topEntry = counts.maxByOrNull { it.value } ?: return DetectedPhase.UNKNOWN
        val majority = PhaseDetectionConfig.PHASE_HISTORY_SIZE / 2 + 1
        return if (topEntry.value >= majority) topEntry.key else DetectedPhase.UNKNOWN
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Samples the entire [frame] bitmap for dominant colour and returns a
     * [PhaseResult].  Prefer passing a pre-cropped action-button region
     * (via [SlotRegions.cropSlot]) for higher accuracy.
     *
     * Also updates the internal history window for smoothed reads via
     * [smoothedPhase].
     */
    fun detect(frame: Bitmap): PhaseResult {
        val hsvResult = detectHsv(frame)
        val result = if (hsvResult.confidence >= 0.05f) hsvResult else detectRgb(frame)

        // Update history window
        if (history.size >= PhaseDetectionConfig.PHASE_HISTORY_SIZE) {
            history.pollFirst()
        }
        history.addLast(result.phase)

        return result
    }

    /**
     * Returns the smoothed phase based on the history majority vote.
     * Call after [detect] to obtain a jitter-free phase classification.
     */
    fun smoothedPhase(): DetectedPhase = historicalMajority()

    /** Reset the history window (call when starting a new draft session). */
    fun resetHistory() = history.clear()

    fun toDraftPhase(detected: DetectedPhase, currentPhase: DraftPhase): DraftPhase =
        when (detected) {
            DetectedPhase.SETUP   -> DraftPhase.SETUP
            DetectedPhase.BAN     -> if (currentPhase == DraftPhase.BAN_ROUND_2) DraftPhase.BAN_ROUND_2 else DraftPhase.BAN_ROUND_1
            DetectedPhase.PICK    -> DraftPhase.PICK
            DetectedPhase.TRADING -> DraftPhase.TRADING
            DetectedPhase.LOADING -> DraftPhase.COMPLETE
            DetectedPhase.UNKNOWN -> currentPhase
        }

    // ── HSV detection (primary) ───────────────────────────────────────────────

    /**
     * Samples pixels in HSV space. Only counts pixels that exceed minimum
     * saturation and value, ensuring pure-grey or very-dark pixels (MLBB UI
     * decorations) are not mistaken for phase indicators.
     */
    private fun detectHsv(frame: Bitmap): PhaseResult {
        val w = frame.width
        val h = frame.height
        val step = 4

        var redHueCount  = 0
        var blueHueCount = 0
        var validSamples = 0
        val hsv = FloatArray(3)

        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val px = frame.getPixel(x, y)
                Color.colorToHSV(px, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                if (sat < PhaseDetectionConfig.HSV_SATURATION_MIN ||
                    value < PhaseDetectionConfig.HSV_VALUE_MIN) continue

                validSamples++

                // Red hue wraps at 0°/360°
                if (hue < PhaseDetectionConfig.BAN_HUE_LOW ||
                    hue > PhaseDetectionConfig.BAN_HUE_HIGH) {
                    redHueCount++
                } else if (hue in PhaseDetectionConfig.PICK_HUE_LOW..PhaseDetectionConfig.PICK_HUE_HIGH) {
                    blueHueCount++
                }
            }
        }

        if (validSamples == 0) return PhaseResult(DetectedPhase.UNKNOWN, 0f)

        val redRatio  = redHueCount.toFloat()  / validSamples
        val blueRatio = blueHueCount.toFloat() / validSamples
        val threshold = PhaseDetectionConfig.HSV_PHASE_RATIO_THRESHOLD

        return when {
            redRatio  > threshold -> PhaseResult(DetectedPhase.BAN,  redRatio.coerceAtMost(1f))
            blueRatio > threshold -> PhaseResult(DetectedPhase.PICK, blueRatio.coerceAtMost(1f))
            else                  -> PhaseResult(DetectedPhase.UNKNOWN, 0f)
        }
    }

    // ── RGB detection (fallback) ──────────────────────────────────────────────

    private fun detectRgb(frame: Bitmap): PhaseResult {
        val w = frame.width
        val h = frame.height

        var redScore  = 0f
        var blueScore = 0f
        var samples   = 0

        val step = 4
        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val px = frame.getPixel(x, y)
                val r  = Color.red(px).toFloat()
                val g  = Color.green(px).toFloat()
                val b  = Color.blue(px).toFloat()

                if (r > PhaseDetectionConfig.BAN_BANNER_RED_MIN && g < 80 && b < 80) redScore++
                if (b > PhaseDetectionConfig.BAN_BANNER_BLUE_MAX && r < 100) blueScore++

                samples++
            }
        }

        if (samples == 0) return PhaseResult(DetectedPhase.UNKNOWN, 0f)

        val redRatio:  Float = redScore  / samples
        val blueRatio: Float = blueScore / samples

        return when {
            redRatio  > 0.06f -> PhaseResult(DetectedPhase.BAN,  redRatio.coerceAtMost(1f))
            blueRatio > 0.06f -> PhaseResult(DetectedPhase.PICK, blueRatio.coerceAtMost(1f))
            else               -> PhaseResult(DetectedPhase.UNKNOWN, 0f)
        }
    }
}
