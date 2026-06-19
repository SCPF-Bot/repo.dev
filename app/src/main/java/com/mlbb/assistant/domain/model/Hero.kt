package com.mlbb.assistant.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
data class Hero(
    val id: Int,
    val name: String,
    val role: String,
    val secondaryRole: String?,
    val lane: Lane,
    val tier: Tier,
    val patchTrend: Double,
    val winRate: Double,
    val pickRate: Double,
    val banRate: Double,
    val imageUrl: String,
    val counters: List<Int>,
    val counteredBy: List<Int>,
    val synergies: List<Int>,
    val recommendedSpells: List<String>,
    val coreItems: List<CoreItem>,
    val flexLanes: List<Lane>,
    val isToxicMechanic: Boolean,
    val isOP: Boolean
)

@Immutable
data class CoreItem(
    val id: Int,
    val name: String,
    val priority: Int
)

enum class Lane(val display: String, val shortLabel: String) {
    EXP("EXP Lane",  "EXP"),
    GOLD("Gold Lane", "GOLD"),
    JUNGLE("Jungle", "JGL"),
    MID("Mid Lane",  "MID"),
    ROAM("Roam",     "ROAM")
}

enum class Tier(val display: String, val order: Int) {
    S_PLUS("S+", 0),
    S("S",       1),
    A_PLUS("A+", 2),
    A("A",       3),
    B("B",       4),
    /**
     * Catch-all for data values not represented above (e.g. "D" in some JSON feeds).
     * Displayed as-is; treated as lowest priority in scoring.
     */
    UNKNOWN("?", 5);

    companion object {
        fun fromString(value: String): Tier = when (value.uppercase().trim()) {
            "S+" -> S_PLUS
            "S"  -> S
            "A+" -> A_PLUS
            "A"  -> A
            "B"  -> B
            else -> UNKNOWN
        }
    }
}
