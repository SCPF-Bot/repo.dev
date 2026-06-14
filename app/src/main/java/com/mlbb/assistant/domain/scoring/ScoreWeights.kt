package com.mlbb.assistant.domain.scoring

data class ScoreWeights(
    val meta: Float    = 0.40f,
    val synergy: Float = 0.30f,
    val counter: Float = 0.30f
) {
    init {
        require((meta + synergy + counter - 1.0f) < 0.01f) {
            "ScoreWeights must sum to 1.0 (got ${meta + synergy + counter})"
        }
    }

    companion object {
        val DEFAULT = ScoreWeights(0.40f, 0.30f, 0.30f)
        val META_HEAVY    = ScoreWeights(0.60f, 0.20f, 0.20f)
        val COUNTER_HEAVY = ScoreWeights(0.30f, 0.20f, 0.50f)
        val SYNERGY_HEAVY = ScoreWeights(0.30f, 0.50f, 0.20f)

        fun normalized(meta: Float, synergy: Float, counter: Float): ScoreWeights {
            val sum = meta + synergy + counter
            return if (sum == 0f) DEFAULT
            else ScoreWeights(meta / sum, synergy / sum, counter / sum)
        }
    }
}
