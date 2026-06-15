package com.mlbb.assistant.domain.scoring

import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.engine.PickTurn
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane

data class HeroScore(
    val hero: Hero,
    val totalScore: Float,
    val metaScore: Float,
    val synergyScore: Float,
    val counterScore: Float,
    val roleScore: Float,
    val badgeLabel: String,   // "◆ META" | "◈ SYNERGY" | "◉ COUNTER" | "◎ BALANCED"
    val reason: String
)

object DraftScorer {

    fun score(
        candidate: Hero,
        alliedPicks: List<Hero>,
        enemyPicks: List<Hero>,
        bannedIds: Set<Int>,
        weights: ScoreWeights,
        missingLanes: List<Lane>,
        currentTurn: PickTurn?
    ): HeroScore {

        // 1. Meta score
        val meta = scoreMetа(candidate)

        // 2. Synergy score
        val synergy = scoreSynergy(candidate, alliedPicks)

        // 3. Counter score
        val counter = scoreCounter(candidate, enemyPicks)

        // 4. Role/lane score
        val role = scoreRole(candidate, missingLanes)

        // 5. Positional modifiers
        val flexBonus = if (currentTurn?.isFirstPick == true) scoreFlexibility(candidate) else 0f
        val safeBonus = if (currentTurn?.isLastPick  == true) scoreSafety(candidate, enemyPicks) else 0f

        val total = (meta     * weights.meta   +
                     synergy  * weights.synergy +
                     counter  * weights.counter +
                     role     * 0.15f           +
                     flexBonus * 0.10f          +
                     safeBonus * 0.10f)
                    .coerceIn(0f, 1f)

        val badge = when {
            meta > synergy && meta > counter       -> "◆ META"
            synergy > meta && synergy > counter    -> "◈ SYNERGY"
            counter > meta && counter > synergy    -> "◉ COUNTER"
            else                                   -> "◎ BALANCED"
        }

        val reason = buildReason(candidate, alliedPicks, enemyPicks, missingLanes)

        return HeroScore(candidate, total, meta, synergy, counter, role, badge, reason)
    }

    /**
     * Simple linear scoring formula used by unit tests and lightweight callers.
     * score = metaWeight * winRate
     *       + counterWeight * (enemies countered / total enemies)
     *       + synergyWeight * (allies synergised / total allies)
     */
    fun computeScore(
        hero: Hero,
        allies: List<Hero>,
        enemies: List<Hero>,
        weights: ScoreWeights
    ): Double {
        val meta    = hero.winRate * weights.meta
        val counter = if (enemies.isEmpty()) 0.0
                      else enemies.count { e -> e.id in hero.counters }.toDouble() / enemies.size * weights.counter
        val synergy = if (allies.isEmpty()) 0.0
                      else allies.count { a -> a.id in hero.synergies }.toDouble() / allies.size * weights.synergy
        return meta + counter + synergy
    }

    fun rankAll(
        pool: List<Hero>,
        alliedPicks: List<Hero>,
        enemyPicks: List<Hero>,
        bannedIds: Set<Int>,
        weights: ScoreWeights,
        currentTurn: PickTurn?
    ): List<HeroScore> {
        val missingLanes = CompositionAnalyzer.getMissingLanes(alliedPicks)
        val available    = pool.filter { it.id !in bannedIds }
        return available
            .map { score(it, alliedPicks, enemyPicks, bannedIds, weights, missingLanes, currentTurn) }
            .sortedByDescending { it.totalScore }
    }

    private fun scoreMetа(hero: Hero): Float {
        val winContrib  = ((hero.winRate  - 0.48f) / 0.08f).coerceIn(0f, 1f)
        val banContrib  = (hero.banRate   / 0.40f).coerceIn(0f, 1f)
        val pickContrib = (hero.pickRate  / 0.30f).coerceIn(0f, 1f)
        val tierContrib = 1f - (hero.tier.order.toFloat() / 4f)
        return (winContrib * 0.35f + banContrib * 0.30f + pickContrib * 0.15f + tierContrib * 0.20f)
    }

    private fun scoreSynergy(candidate: Hero, allies: List<Hero>): Float {
        if (allies.isEmpty()) return 0f
        val allySynergyCount = allies.count { ally -> candidate.id in ally.synergies }
        val candSynergyCount = allies.count { ally -> ally.id in candidate.synergies }
        val matchCount = (allySynergyCount + candSynergyCount).toFloat()
        return (matchCount / (allies.size * 2f)).coerceIn(0f, 1f)
    }

    private fun scoreCounter(candidate: Hero, enemies: List<Hero>): Float {
        if (enemies.isEmpty()) return 0f
        val counterCount = enemies.count { enemy -> enemy.id in candidate.counters }
        return (counterCount.toFloat() / enemies.size.toFloat()).coerceIn(0f, 1f)
    }

    private fun scoreRole(candidate: Hero, missingLanes: List<Lane>): Float =
        if (candidate.lane in missingLanes) 1.0f
        else if (candidate.flexLanes.any { it in missingLanes }) 0.6f
        else 0f

    private fun scoreFlexibility(hero: Hero): Float {
        val counterableCount = hero.counteredBy.size.toFloat()
        return (1f - counterableCount / 10f).coerceIn(0f, 1f)
    }

    private fun scoreSafety(hero: Hero, enemies: List<Hero>): Float {
        val countersCount = enemies.count { e -> e.id in hero.counters }.toFloat()
        return (countersCount / enemies.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun buildReason(
        hero: Hero, allies: List<Hero>, enemies: List<Hero>, missing: List<Lane>
    ): String {
        val synAlly  = allies.firstOrNull { hero.id in it.synergies }
        val counters = enemies.filter { e -> e.id in hero.counters }
        return when {
            synAlly != null && counters.isNotEmpty() ->
                "Synergizes with ${synAlly.name} + counters ${counters.first().name}"
            synAlly != null  -> "Strong combo with ${synAlly.name}"
            counters.isNotEmpty() ->
                "Direct counter to ${counters.take(2).joinToString(", ") { it.name }}"
            hero.lane in missing -> "Fills missing ${hero.lane.display} role"
            hero.isOP            -> "Top meta pick this patch"
            else                 -> "Solid meta choice — %.0f%% win rate".format(hero.winRate * 100)
        }
    }
}
