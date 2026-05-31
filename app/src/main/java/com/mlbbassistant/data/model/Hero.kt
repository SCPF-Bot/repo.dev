package com.mlbbassistant.data.model

/**
 * Domain model representing a single MLBB hero and its draft metadata.
 *
 * @param id          Unique hero identifier (matches API / DB primary key).
 * @param name        Display name of the hero.
 * @param role        Primary role classification.
 * @param secondaryRole Optional secondary role (e.g. Fighter/Assassin).
 * @param winRate     Decimal win-rate in [0, 1].
 * @param pickRate    Decimal pick-rate in [0, 1].
 * @param banRate     Decimal ban-rate in [0, 1].
 * @param counters    IDs of heroes this hero hard-counters.
 * @param counteredBy IDs of heroes that hard-counter this hero.
 * @param synergies   IDs of heroes with strong synergy.
 * @param lane        Preferred lane / position.
 * @param imageUrl    Remote URL for the hero portrait (may be empty for offline fallback data).
 */
data class Hero(
    val id: Int,
    val name: String,
    val role: HeroRole,
    val secondaryRole: HeroRole? = null,
    val winRate: Float = 0.50f,
    val pickRate: Float = 0f,
    val banRate: Float = 0f,
    val counters: List<Int> = emptyList(),
    val counteredBy: List<Int> = emptyList(),
    val synergies: List<Int> = emptyList(),
    val lane: HeroLane = HeroLane.JUNGLE,
    val imageUrl: String = ""
)
