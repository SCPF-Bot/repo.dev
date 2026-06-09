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

        val counterScore = if (enemies.isNotEmpty()) {
            enemies.count { enemy -> hero.counters.contains(enemy.id) }.toDouble() / enemies.size
        } else 0.0

        val synergyScore = if (allies.isNotEmpty()) {
            allies.count { ally -> hero.synergies.contains(ally.id) }.toDouble() / allies.size
        } else 0.0

        return (weights.metaWeight * metaScore) +
                (weights.counterWeight * counterScore) +
                (weights.synergyWeight * synergyScore)
    }
}