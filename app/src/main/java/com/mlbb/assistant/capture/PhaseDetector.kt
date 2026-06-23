package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.mlbb.assistant.domain.engine.DraftPhase

/**
 * Detects the current MLBB draft phase from a screen frame.
 *
 * Strategy:
 *  - Caller is responsible for passing the relevant crop (e.g. the action
 *    button region from [SlotRegions.actionButton]).  [detect] samples the
 *    **entire** bitmap it receives, so passing a pre-cropped region gives
 *    accurate results without coupling this detector to any specific layout.
 *  - Red dominant   → BAN phase
 *  - Blue dominant  → PICK phase
 *  - Neither        → UNKNOWN
 *
 * All magic-number thresholds have been moved to [PhaseDetectionConfig]
 * (TD-03) so patch-update calibration does not require reading detector logic.
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

    /**
     * Samples the entire [frame] bitmap for dominant colour and returns a
     * [PhaseResult].  Pass the full screen frame only if the action button
     * region is not separately cropped; prefer passing a cropped action button
     * region (via [SlotRegions.cropSlot]) for higher accuracy.
     *
     * Thresholds reference [PhaseDetectionConfig] named constants (TD-03).
     */
    fun detect(frame: Bitmap): PhaseResult {
        val w = frame.width
        val h = frame.height

        var redScore  = 0f
        var blueScore = 0f
        var samples   = 0

        val step = 4 // every 4th pixel for speed
        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                if (x >= w || y >= h) continue
                val px = frame.getPixel(x, y)
                val r  = Color.red(px).toFloat()
                val g  = Color.green(px).toFloat()
                val b  = Color.blue(px).toFloat()

                // Red: r dominates — uses BAN_BANNER_RED_MIN (TD-03).
                if (r > PhaseDetectionConfig.BAN_BANNER_RED_MIN && g < 80 && b < 80) redScore++

                // Blue: b dominates — uses BAN_BANNER_BLUE_MAX (TD-03).
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

    fun toDraftPhase(detected: DetectedPhase, currentPhase: DraftPhase): DraftPhase =
        when (detected) {
            DetectedPhase.SETUP   -> DraftPhase.SETUP
            DetectedPhase.BAN     -> if (currentPhase == DraftPhase.BAN_ROUND_2) DraftPhase.BAN_ROUND_2 else DraftPhase.BAN_ROUND_1
            DetectedPhase.PICK    -> DraftPhase.PICK
            DetectedPhase.TRADING -> DraftPhase.TRADING
            DetectedPhase.LOADING -> DraftPhase.COMPLETE
            DetectedPhase.UNKNOWN -> currentPhase
        }
}
