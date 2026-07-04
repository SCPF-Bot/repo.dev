package com.mlbb.assistant.domain.model

/**
 * Typed enum for MLBB hero roles, replacing bare string comparisons throughout
 * the advisor layer.
 *
 * Previously, [com.mlbb.assistant.domain.advisor.BuildAdvisor] and
 * [com.mlbb.assistant.domain.advisor.BanUrgencyScorer] compared `hero.role`
 * against string literals like `"Mage"` or `hero.role in listOf("Tank", "Support")`.
 * Any hero-data normalization change (capitalisation, typo) silently broke
 * recommendations. [HeroRole.fromString] centralises the mapping.
 */
enum class HeroRole(val display: String) {
    MAGE("Mage"),
    MARKSMAN("Marksman"),
    TANK("Tank"),
    FIGHTER("Fighter"),
    SUPPORT("Support"),
    ASSASSIN("Assassin"),
    UNKNOWN("Unknown");

    companion object {
        /**
         * Case-insensitive lookup — returns [UNKNOWN] for unrecognised strings
         * so callers degrade gracefully instead of crashing.
         */
        fun fromString(value: String): HeroRole =
            entries.firstOrNull { it.display.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Convenience computed property — avoids repeated [HeroRole.fromString] call sites. */
val Hero.roleEnum: HeroRole get() = HeroRole.fromString(role)
