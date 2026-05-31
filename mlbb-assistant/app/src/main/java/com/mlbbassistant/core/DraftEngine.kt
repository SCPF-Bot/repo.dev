package com.mlbbassistant.core

import com.mlbbassistant.data.model.DraftState
import com.mlbbassistant.data.model.DraftSuggestion
import com.mlbbassistant.data.model.Hero
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless scoring engine that produces ranked [DraftSuggestion]s given a
 * pool of available heroes and the current [DraftState].
 *
 * Scoring formula (all weights configurable via [Weights]):
 *   score = wMeta * metaScore + wCounter * counterScore + wSynergy * synergyScore
 *
 * Each sub-score is normalised to [0, 1] before weighting.
 */
@Singleton
class DraftEngine @Inject constructor() {

    data class Weights(
        val meta: Float    = 0.35f,
        val counter: Float = 0.40f,
        val synergy: Float = 0.25f
    )

    /**
     * @param pool     All heroes NOT already picked or banned (i.e. available).
     * @param state    Current draft state.
     * @param topN     Maximum number of suggestions to return.
     * @param weights  Scoring weights.
     */
    fun suggest(
        pool: List<Hero>,
        state: DraftState,
        topN: Int = 5,
        weights: Weights = Weights()
    ): List<DraftSuggestion> {
        if (pool.isEmpty()) return emptyList()

        val enemyIds  = state.enemyPicks.map { it.id }.toSet()
        val allyIds   = state.allyPicks.map { it.id }.toSet()

        return pool.map { hero ->
            val meta    = metaScore(hero)
            val counter = counterScore(hero, enemyIds)
            val synergy = synergyScore(hero, allyIds)
            val score   = weights.meta * meta + weights.counter * counter + weights.synergy * synergy

            DraftSuggestion(
                hero         = hero,
                score        = score,
                reason       = buildReason(hero, counter, synergy, meta),
                counterScore = counter,
                synergyScore = synergy,
                metaScore    = meta
            )
        }
            .sortedByDescending { it.score }
            .take(topN)
    }

    // -------------------------------------------------------------------------
    // Private sub-scorers
    // -------------------------------------------------------------------------

    /** Win-rate weighted by inverse of ban-rate (high ban = slight penalty). */
    private fun metaScore(hero: Hero): Float {
        val winNorm = hero.winRate.coerceIn(0f, 1f)
        val banPenalty = (1f - hero.banRate.coerceIn(0f, 1f)) * 0.2f
        return (winNorm + banPenalty) / 1.2f
    }

    /** Fraction of enemy picks that this hero hard-counters. */
    private fun counterScore(hero: Hero, enemyIds: Set<Int>): Float {
        if (enemyIds.isEmpty()) return 0f
        val countered = hero.counters.count { it in enemyIds }
        return countered.toFloat() / enemyIds.size
    }

    /** Fraction of ally picks that this hero has listed synergy with. */
    private fun synergyScore(hero: Hero, allyIds: Set<Int>): Float {
        if (allyIds.isEmpty()) return 0f
        val synCount = hero.synergies.count { it in allyIds }
        return synCount.toFloat() / allyIds.size
    }

    private fun buildReason(
        hero: Hero,
        counter: Float,
        synergy: Float,
        meta: Float
    ): String = buildString {
        if (counter >= 0.5f) append("Counters multiple enemy heroes. ")
        if (synergy >= 0.5f) append("Strong synergy with your team. ")
        if (meta >= 0.55f)   append("High win rate this patch. ")
        if (isEmpty())       append("Balanced pick for current draft.")
    }.trimEnd()
}
