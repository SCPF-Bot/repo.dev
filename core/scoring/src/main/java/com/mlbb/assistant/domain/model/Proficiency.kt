package com.mlbb.assistant.domain.model

/**
 * Player proficiency level for a hero in the personal hero pool.
 *
 * [scoreMultiplier] is applied to the final [DraftScorer] recommendation
 * score when the personal-pool feature is active (i.e. the user has added
 * at least one hero to their pool).  If the pool is empty the multiplier is
 * never applied — all heroes score on equal footing.
 *
 * NONE represents a hero that is *not* in the pool at all; it receives a
 * meaningful downweight so the recommendation engine naturally avoids
 * suggesting heroes the user cannot play.
 */
enum class Proficiency(val display: String, val scoreMultiplier: Float) {
    NONE("Not in pool",   0.50f),
    LEARNING("Learning",  0.80f),
    COMFORTABLE("Comfortable", 0.95f),
    MASTERED("Mastered", 1.10f);

    companion object {
        fun fromString(value: String): Proficiency =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}
