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
        val availableHeroes = allHeroes.filter { hero ->
            !allies.contains(hero) && !enemies.contains(hero) && hero.id !in bannedIds
        }
        return availableHeroes.map { hero ->
            hero to scorer.computeScore(hero, allies, enemies, weights)
        }.sortedByDescending { it.second }
    }
}