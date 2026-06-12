package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.DraftScorer
import com.mlbb.assistant.domain.scoring.ScoreWeights
import javax.inject.Inject

class GetSuggestionsUseCase @Inject constructor(
    private val scorer: DraftScorer
) {
    operator fun invoke(
        allHeroes: List<Hero>,
        allies: List<Hero>,
        enemies: List<Hero>,
        weights: ScoreWeights,
        bannedIds: List<Int> = emptyList()
    ): List<Pair<Hero, Double>> {
        // Use Set for O(1) banned-id lookup instead of O(n) per hero
        val bannedSet = bannedIds.toHashSet()
        val allyIds = allies.map { it.id }.toHashSet()
        val enemyIds = enemies.map { it.id }.toHashSet()

        val availableHeroes = allHeroes.filter { hero ->
            hero.id !in allyIds && hero.id !in enemyIds && hero.id !in bannedSet
        }
        return availableHeroes.map { hero ->
            hero to scorer.computeScore(hero, allies, enemies, weights)
        }.sortedByDescending { it.second }
    }
}
