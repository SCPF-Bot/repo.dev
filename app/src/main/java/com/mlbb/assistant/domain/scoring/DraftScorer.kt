package com.mlbb.assistant.domain.scoring

import com.mlbb.assistant.domain.model.Hero
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftScorer @Inject constructor() {

    fun computeScore(
        hero: Hero,
        allies: List<Hero>,
        enemies: List<Hero>,
        weights: ScoreWeights
    ): Double {
        val metaScore = hero.winRate

        // Convert to Set for O(1) lookup instead of O(n) List.contains per hero
        val heroCounterSet = hero.counters.toHashSet()
        val heroSynergySet = hero.synergies.toHashSet()

        val counterScore = if (enemies.isNotEmpty()) {
            enemies.count { enemy -> heroCounterSet.contains(enemy.id) }.toDouble() / enemies.size
        } else 0.0

        val synergyScore = if (allies.isNotEmpty()) {
            allies.count { ally -> heroSynergySet.contains(ally.id) }.toDouble() / allies.size
        } else 0.0

        return (weights.metaWeight * metaScore) +
                (weights.counterWeight * counterScore) +
                (weights.synergyWeight * synergyScore)
    }
}
