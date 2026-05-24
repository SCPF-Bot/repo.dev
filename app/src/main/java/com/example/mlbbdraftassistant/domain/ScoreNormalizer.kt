package com.example.mlbbdraftassistant.domain

/**
 * Utility to normalise raw scores into a 0..1 range.
 * All sub‑scores must be normalised so they can be fairly combined with weights.
 */
object ScoreNormalizer {

    /**
     * Normalise a value against a maximum possible value.
     * If the value exceeds the max, it is capped at 1.0.
     */
    fun normalise(value: Float, max: Float): Float {
        if (max <= 0f) return 0f
        return (value / max).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------------
    // Convenience methods for each sub‑score (used later in the engine)
    // ------------------------------------------------------------------------

    /**
     * Normalise synergy: total raw synergy divided by (number of allies * max possible synergy per hero).
     */
    fun synergyNormalised(rawSum: Float, allyCount: Int, maxSynergyPerAlly: Float = 5f): Float {
        if (allyCount == 0) return 0f
        return normalise(rawSum, allyCount * maxSynergyPerAlly)
    }

    /**
     * Normalise counter: total raw counter strength divided by (number of enemies * max counter per enemy).
     */
    fun counterNormalised(rawSum: Float, enemyCount: Int, maxCounterPerEnemy: Float = 5f): Float {
        if (enemyCount == 0) return 0f
        return normalise(rawSum, enemyCount * maxCounterPerEnemy)
    }

    /**
     * Normalise role balance: a penalty between 0 (worst) and 1 (perfect).
     * 1 = no role overstack, 0 = too many of the same role.
     * This will be computed in the engine based on team composition.
     */
    fun roleBalanceNormalised(roleCount: Int, maxAllowed: Int = 2): Float {
        return if (roleCount <= maxAllowed) 1f
        else (1f / (roleCount - maxAllowed + 1)).coerceAtLeast(0f)
    }

    /**
     * Normalise meta score from win/pick/ban rates.
     * Assumes rates are already percentages (0‑100).
     */
    fun metaNormalised(winRate: Float, pickRate: Float, banRate: Float): Float {
        val raw = (winRate + pickRate * 0.3f + banRate * 0.5f)
        // Normalise to 0‑1 assuming max raw ≈ 100 + 30 + 50 = 180
        return normalise(raw, 180f)
    }
}