package com.mlbb.assistant.domain.scoring

/**
 * User-configurable scoring weights for the draft advisor.
 *
 * Weights must sum to 1.0 for the composite score to remain in [0, 1].
 * The default split (meta 40%, counter 30%, synergy 30%) is applied when
 * the user has not yet configured preferences via the Settings screen.
 */
data class ScoreWeights(
    val meta:    Float = 0.40f,
    val counter: Float = 0.30f,
    val synergy: Float = 0.30f
) {
    init {
        val sum = meta + counter + synergy
        require(sum in 0.99f..1.01f) {
            "ScoreWeights must sum to 1.0 — got $sum (meta=$meta, counter=$counter, synergy=$synergy)"
        }
    }

    companion object {
        val DEFAULT = ScoreWeights()
    }
}
