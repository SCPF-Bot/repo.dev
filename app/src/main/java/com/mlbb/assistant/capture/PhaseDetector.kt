package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.mlbb.assistant.domain.engine.DraftPhase

/**
 * Detects the current MLBB draft phase from a screen frame.
 *
 * Strategy:
 *  - Samples a representative region of the screen where the
 *    active action button appears (lower-centre).
 *  - Red dominant   → BAN phase
 *  - Blue/green dom → PICK phase
 *  - Neither        → retain previous or IDLE
 *
 * Also detects the setup countdown text region for SETUP phase.
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
     * Samples the bottom-centre 15% of the screen width × 10% height band.
     * MLBB's Ban button is red (#CC2233-ish), Pick button is blue (#2255CC-ish).
     */
    fun detect(frame: Bitmap): PhaseResult {
        val w = frame.width
        val h = frame.height

        // Sample region: bottom 10–20% of screen, centre third
        val left   = (w * 0.33).toInt()
        val right  = (w * 0.67).toInt()
        val top    = (h * 0.80).toInt()
        val bottom = (h * 0.92).toInt()

        var redScore  = 0f
        var blueScore = 0f
        var samples   = 0

        val step = 4 // every 4th pixel for speed
        for (x in left until right step step) {
            for (y in top until bottom step step) {
                if (x >= w || y >= h) continue
                val px = frame.getPixel(x, y)
                val r  = Color.red(px).toFloat()
                val g  = Color.green(px).toFloat()
                val b  = Color.blue(px).toFloat()

                // Red: r dominates, g+b low
                if (r > 160 && g < 80 && b < 80) redScore++
                // Blue: b dominates, r+g moderate
                if (b > 140 && r < 100) blueScore++
                samples++
            }
        }

        if (samples == 0) return PhaseResult(DetectedPhase.UNKNOWN, 0f)

        val redRatio  = redScore  / samples
        val blueRatio = blueScore / samples

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
