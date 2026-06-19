package com.mlbb.assistant.domain.model

/**
 * MLBB map lanes / roles used for team-composition analysis.
 */
enum class Lane(val label: String) {
    EXP("EXP Lane"),
    JUNGLE("Jungle"),
    MID("Mid Lane"),
    GOLD("Gold Lane"),
    ROAM("Roam");
}
