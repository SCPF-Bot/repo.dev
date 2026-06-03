package com.mlbbassistant.data.model

/**
 * Snapshot of an ongoing draft session.
 *
 * @param allyPicks   Heroes already locked in by the player's team (max 5).
 * @param enemyPicks  Heroes already locked in by the enemy team (max 5).
 * @param bans        All banned heroes regardless of team (max 10).
 */
data class DraftState(
    val allyPicks: List<Hero>  = emptyList(),
    val enemyPicks: List<Hero> = emptyList(),
    val bans: List<Hero>       = emptyList()
) {
    val allPicked: List<Hero> get() = allyPicks + enemyPicks
    val allUnavailable: List<Hero> get() = allPicked + bans
    val unavailableIds: Set<Int> get() = allUnavailable.map { it.id }.toSet()
    val isComplete: Boolean get() = allyPicks.size == 5 && enemyPicks.size == 5
}
