package com.mlbb.assistant.domain.advisor

/**
 * Kotlin port of the trait-based counter scoring system from
 * AlanNobita/mlbb_drafter (server/recommendation/scoring.py).
 *
 * For each enemy in the draft, the engine cross-references the enemy's
 * threat traits against the candidate hero's counter-traits.  Each
 * successful (threat, counter) match adds [TRAIT_BONUS_PER_MATCH] to the
 * candidate's score, capped at [MAX_TRAIT_BONUS].
 *
 * ## Counter-trait matrix
 * | Enemy threat trait  | Candidate counter traits                       |
 * |---------------------|------------------------------------------------|
 * | high_sustain        | anti_heal, true_damage                         |
 * | high_armor          | armor_shred, true_damage                       |
 * | squishy_burst       | high_burst, backline_threat, dive              |
 * | high_mobility       | crowd_control, anti_dash, suppress             |
 * | hard_engage         | disengage, anti_cc, poke_kite                 |
 * | heavy_shields       | shield_breaker, true_damage                    |
 * | poke_kite           | dive, high_mobility, backline_threat          |
 *
 * The matrix matches real MLBB itemisation and counter-pick philosophy
 * as observed across multiple open-source draft-assistant projects.
 *
 * Usage:
 * ```kotlin
 * val bonus = TraitCounterEngine.computeBonus(candidateTraits, enemyTraits)
 * score += bonus  // bonus already clamped to MAX_TRAIT_BONUS
 * ```
 */
object TraitCounterEngine {

    const val TRAIT_BONUS_PER_MATCH: Float = 0.15f
    const val MAX_TRAIT_BONUS: Float       = 0.45f

    /**
     * Cross-reference map: enemy threat → set of candidate counter-traits.
     *
     * "backline_threat" is the canonical tag for Assassin-type heroes that
     * threaten squishy backline targets.  It avoids colliding with the
     * "assassin" role string used elsewhere.
     */
    private val COUNTER_TRAIT_MAP: Map<String, Set<String>> = mapOf(
        "high_sustain"  to setOf("anti_heal", "true_damage"),
        "high_armor"    to setOf("armor_shred", "true_damage"),
        "squishy_burst" to setOf("high_burst", "backline_threat", "dive"),
        "high_mobility" to setOf("crowd_control", "anti_dash", "suppress"),
        "hard_engage"   to setOf("disengage", "anti_cc", "poke_kite"),
        "heavy_shields" to setOf("shield_breaker", "true_damage"),
        "poke_kite"     to setOf("dive", "high_mobility", "backline_threat")
    )

    /**
     * Computes the trait counter bonus for a candidate hero given the
     * current enemy composition.
     *
     * @param candidateTraits  Trait set for the hero being evaluated.
     * @param enemyTraitsList  List of trait sets, one per enemy hero.
     *
     * @return Raw trait bonus clamped to [MAX_TRAIT_BONUS].
     *         Returns 0.0 if [candidateTraits] or [enemyTraitsList] is empty.
     */
    fun computeBonus(
        candidateTraits: Set<String>,
        enemyTraitsList: List<Set<String>>
    ): Float {
        if (candidateTraits.isEmpty() || enemyTraitsList.isEmpty()) return 0f

        var bonus = 0f
        for (enemyTraits in enemyTraitsList) {
            for ((threatTrait, counterTraits) in COUNTER_TRAIT_MAP) {
                if (threatTrait in enemyTraits && candidateTraits.intersect(counterTraits).isNotEmpty()) {
                    bonus += TRAIT_BONUS_PER_MATCH
                }
            }
        }
        return bonus.coerceAtMost(MAX_TRAIT_BONUS)
    }

    /**
     * Convenience overload that accepts a flat merged set of all enemy traits
     * instead of per-enemy sets.  Treats the merged set as if it came from
     * one conceptual "enemy team" entity.
     */
    fun computeBonusFlat(
        candidateTraits: Set<String>,
        allEnemyTraits: Set<String>
    ): Float {
        if (candidateTraits.isEmpty() || allEnemyTraits.isEmpty()) return 0f

        var bonus = 0f
        for ((threatTrait, counterTraits) in COUNTER_TRAIT_MAP) {
            if (threatTrait in allEnemyTraits && candidateTraits.intersect(counterTraits).isNotEmpty()) {
                bonus += TRAIT_BONUS_PER_MATCH
            }
        }
        return bonus.coerceAtMost(MAX_TRAIT_BONUS)
    }

    /**
     * Returns a human-readable description of why [candidateTraits] counters
     * [allEnemyTraits].  Used to populate [HeroScore.reason] with trait-based
     * explanations in the overlay suggestion cards.
     *
     * Returns null if no matching threat/counter pair exists.
     */
    fun describeCounters(
        candidateTraits: Set<String>,
        allEnemyTraits: Set<String>
    ): String? {
        val matches = mutableListOf<String>()
        for ((threatTrait, counterTraits) in COUNTER_TRAIT_MAP) {
            if (threatTrait in allEnemyTraits) {
                val matched = candidateTraits.intersect(counterTraits)
                if (matched.isNotEmpty()) {
                    matches += traitLabel(threatTrait)
                }
            }
        }
        if (matches.isEmpty()) return null
        return "Trait counters: " + matches.joinToString(", ")
    }

    private fun traitLabel(trait: String): String = when (trait) {
        "high_sustain"  -> "enemy sustain (anti-heal)"
        "high_armor"    -> "enemy armor stack (armor shred)"
        "squishy_burst" -> "protects squishy allies"
        "high_mobility" -> "enemy mobility (CC/suppress)"
        "hard_engage"   -> "enemy engage (disengage tools)"
        "heavy_shields" -> "enemy shields (shield break)"
        "poke_kite"     -> "enemy poke (gap close)"
        else            -> trait.replace('_', ' ')
    }
}
