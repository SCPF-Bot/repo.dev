package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.domain.scoring.ScoreWeights
import kotlin.math.abs

/**
 * Analyses recent [DraftHistoryItem] records to produce a calibrated
 * [ScoreWeights] suggestion.
 *
 * Algorithm:
 * 1. Separate sessions by outcome (WIN / LOSS).
 * 2. Compare the average component scores (meta / counter / synergy) between
 *    winning and losing sessions.
 * 3. Increase the weight for a component where the winning sessions score
 *    significantly higher than losing ones, capped at ±0.15 adjustment per
 *    calibration run.
 * 4. Normalise the result so meta + synergy + counter = 1.0.
 *
 * The calibrator requires at least [MIN_SESSIONS] outcomes recorded to
 * avoid over-fitting on tiny samples.
 *
 * DRAW sessions are excluded from calibration because they carry no
 * directional signal.
 */
object WeightCalibrator {

    private const val MIN_SESSIONS = 10
    private const val MAX_DELTA    = 0.15f
    private const val LEARN_RATE   = 0.50f

    data class CalibrationResult(
        val suggestedWeights: ScoreWeights,
        val confidence: Float,          // 0..1 — higher = more history
        val rationale: String
    )

    /**
     * Produces a [CalibrationResult] from [history].
     * Returns null if there are fewer than [MIN_SESSIONS] sessions with
     * known WIN or LOSS outcomes.
     */
    fun calibrate(history: List<DraftHistoryItem>, current: ScoreWeights): CalibrationResult? {
        val labelled = history.filter { it.outcome == DraftOutcome.WIN || it.outcome == DraftOutcome.LOSS }
        if (labelled.size < MIN_SESSIONS) return null

        val wins   = labelled.filter { it.outcome == DraftOutcome.WIN }
        val losses = labelled.filter { it.outcome == DraftOutcome.LOSS }

        if (wins.isEmpty() || losses.isEmpty()) return null

        val avgMeta    = diff(wins, losses) { it.metaScore.toFloat() }
        val avgCounter = diff(wins, losses) { it.counterScore.toFloat() }
        val avgSynergy = diff(wins, losses) { it.synergyScore.toFloat() }

        val total = abs(avgMeta) + abs(avgCounter) + abs(avgSynergy)
        if (total < 0.001f) {
            return CalibrationResult(
                current, 0f,
                "No meaningful pattern detected yet — keep playing to refine."
            )
        }

        val newMeta    = (current.meta    + clampDelta(avgMeta    / total * LEARN_RATE)).coerceAtLeast(0.05f)
        val newCounter = (current.counter + clampDelta(avgCounter / total * LEARN_RATE)).coerceAtLeast(0.05f)
        val newSynergy = (current.synergy + clampDelta(avgSynergy / total * LEARN_RATE)).coerceAtLeast(0.05f)

        // Argument order matches ScoreWeights.normalized(meta, synergy, counter).
        val suggested  = ScoreWeights.normalized(newMeta, newSynergy, newCounter)
        val confidence = (labelled.size.toFloat() / (labelled.size + 20)).coerceIn(0f, 1f)

        val dominant = listOf(
            "Meta" to avgMeta, "Counter" to avgCounter, "Synergy" to avgSynergy
        ).maxByOrNull { abs(it.second) }

        val rationale = dominant?.let {
            "Winning drafts had ${if (it.second > 0) "higher" else "lower"} ${it.first} scores. " +
            "Adjusting weight ${if (it.second > 0) "up" else "down"}."
        } ?: "Balanced signal across all components."

        return CalibrationResult(suggested, confidence, rationale)
    }

    private fun diff(wins: List<DraftHistoryItem>, losses: List<DraftHistoryItem>,
                     extractor: (DraftHistoryItem) -> Float): Float {
        val avgWin  = wins.map(extractor).average().toFloat()
        val avgLoss = losses.map(extractor).average().toFloat()
        return avgWin - avgLoss
    }

    private fun clampDelta(delta: Float): Float = delta.coerceIn(-MAX_DELTA, MAX_DELTA)
}
