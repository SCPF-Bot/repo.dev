package com.mlbb.assistant.domain.model

/**
 * Meta tier for MLBB heroes.
 *
 * FIX: Added [D] tier value so [fromString] no longer silently maps "D" to [B].
 * Previously, any unknown tier string (including the valid JSON tier "D") would
 * fall through to [B], causing data loss in the hero list.
 *
 * [rank] maps each tier to a numeric sort key used in Room CASE expressions
 * (see HeroDao) — lower is better (S+ = 0).
 */
enum class Tier(val label: String, val rank: Int) {
    S_PLUS("S+", 0),
    S("S",     1),
    A_PLUS("A+", 2),
    A("A",     3),
    B("B",     4),
    D("D",     5);

    companion object {
        /**
         * Returns the [Tier] whose [label] matches [value] (case-sensitive).
         * Falls back to [B] only for truly unknown values; "D" now maps to [D].
         */
        fun fromString(value: String): Tier =
            entries.firstOrNull { it.label == value } ?: B
    }
}
