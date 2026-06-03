package com.mlbbassistant.data.model

enum class HeroLane(val displayName: String) {
    GOLD("Gold Lane"),
    EXP("Exp Lane"),
    JUNGLE("Jungle"),
    MID("Mid Lane"),
    ROAM("Roam");

    companion object {
        fun fromString(value: String): HeroLane =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: JUNGLE
    }
}
