package com.mlbbassistant.data.model

enum class HeroRole(val displayName: String) {
    TANK("Tank"),
    FIGHTER("Fighter"),
    ASSASSIN("Assassin"),
    MAGE("Mage"),
    MARKSMAN("Marksman"),
    SUPPORT("Support"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String): HeroRole =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
