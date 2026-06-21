package com.mlbb.assistant.domain.scoring

import androidx.compose.runtime.Stable
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.engine.PickTurn
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Proficiency
import com.mlbb.assistant.domain.model.Tier

/**
 * Per-hero recommendation score emitted by [DraftScorer].
 *
 * @Stable tells the Compose compiler all public fields have stable types,
 * enabling skipping of recomposition when the instance reference is unchanged.
 */
@Stable
data class HeroScore(
    val hero: Hero,
    val totalScore: Float,
    val metaScore: Float,
    val synergyScore: Float,
    val counterScore: Float,
    val roleScore: Float,
    val badgeLabel: String,   // "↑ RISING" | "◆ META" | "◈ SYNERGY" | "◉ COUNTER" | "◎ BALANCED"
    val reason: String
)

object DraftScorer {

    /**
     * Maximum tier order value — used to normalise tier contribution inside
     * [scoreMeta] so results are always in [0, 1].
     */
    private val TIER_MAX_ORDER: Float = Tier.entries.maxOf { it.order }.toFloat()

    /**
     * TD-05: Dynamic scoring bounds derived from the current hero dataset.
     *
     * Replaces the previous hardcoded thresholds (winRate 0.48/0.08,
     * banRate 0.40, pickRate 0.30) with median ± IQR-based normalisation
     * so the scoring is robust to future stat shifts.
     */
    data class ScoreBounds(
        val winRateMedian: Float,
        val winRateScale: Float,    // IQR or fallback range
        val banRateCap: Float,
        val pickRateCap: Float
    ) {
        companion object {
            /** Fallback bounds when pool is empty or lacks variance. */
            val DEFAULT = ScoreBounds(
                winRateMedian = 0.50f,
                winRateScale  = 0.08f,
                banRateCap    = 0.40f,
                pickRateCap   = 0.30f
            )
        }
    }

    /**
     * Computes [ScoreBounds] from a pool of heroes.
     *
     * winRateMedian = median of all heroes' winRates
     * winRateScale  = (Q3 − Q1) / 2  (half-IQR), min-clamped to 0.02
     * banRateCap    = 90th-percentile ban rate in the pool
     * pickRateCap   = 90th-percentile pick rate in the pool
     */
    fun computeBounds(pool: List<Hero>): ScoreBounds {
        if (pool.size < 4) return ScoreBounds.DEFAULT

        val wins  = pool.map { it.winRate.toFloat() }.sorted()
        val bans  = pool.map { it.banRate.toFloat() }.sorted()
        val picks = pool.map { it.pickRate.toFloat() }.sorted()
        val n     = pool.size

        val median = wins[n / 2]
        val q1     = wins[n / 4]
        val q3     = wins[n * 3 / 4]
        val scale  = ((q3 - q1) / 2f).coerceAtLeast(0.02f)

        val p90idx   = (n * 0.90f).toInt().coerceIn(0, n - 1)
        val banCap   = bans[p90idx].coerceAtLeast(0.05f)
        val pickCap  = picks[p90idx].coerceAtLeast(0.05f)

        return ScoreBounds(
            winRateMedian = median,
            winRateScale  = scale,
            banRateCap    = banCap,
            pickRateCap   = pickCap
        )
    }

    /**
     * Computes a recommendation score for a single [candidate] hero.
     *
     * @param pickIndex     0-based index of the current pick in the sequence.
     * @param maxPickIndex  Total picks in the draft (default 10 for 5v5).
     * @param poolMap       Personal hero pool proficiency map (TD-02).
     * @param bounds        Dataset-derived normalisation bounds (TD-05).
     */
    fun score(
        candidate: Hero,
        alliedPicks: List<Hero>,
        enemyPicks: List<Hero>,
        bannedIds: Set<Int>,
        weights: ScoreWeights,
        missingLanes: List<Lane>,
        currentTurn: PickTurn?,
        pickIndex: Int = 0,
        maxPickIndex: Int = 10,
        poolMap: Map<Int, Proficiency> = emptyMap(),
        bounds: ScoreBounds = ScoreBounds.DEFAULT
    ): HeroScore {

        // Adaptive weights: shift from meta-heavy → synergy+counter-heavy.
        val adaptedWeights = adaptiveWeights(weights, pickIndex, maxPickIndex)

        // 1. Meta score (includes patch velocity multiplier + dynamic bounds)
        val meta = scoreMeta(candidate, bounds)

        // 2. Synergy score
        val synergy = scoreSynergy(candidate, alliedPicks)

        // 3. Counter score
        val counter = scoreCounter(candidate, enemyPicks)

        // 4. Role/lane score
        val role = scoreRole(candidate, missingLanes)

        // 5. Positional modifiers
        val flexBonus = if (currentTurn?.isFirstPick == true) scoreFlexibility(candidate) else 0f
        val safeBonus = if (currentTurn?.isLastPick  == true) scoreSafety(candidate, enemyPicks) else 0f

        var total = (meta     * adaptedWeights.meta    +
                     synergy  * adaptedWeights.synergy +
                     counter  * adaptedWeights.counter +
                     role     * 0.15f                  +
                     flexBonus * 0.10f                 +
                     safeBonus * 0.10f)
                    .coerceIn(0f, 1f)

        // TD-02: Personal pool multiplier
        if (poolMap.isNotEmpty()) {
            val proficiency = poolMap[candidate.id] ?: Proficiency.NONE
            total = (total * proficiency.scoreMultiplier).coerceIn(0f, 1f)
        }

        // Section 4.2.2: Rising This Patch badge — highest priority when patchTrend ≥ 0.10.
        val badge = when {
            candidate.patchTrend >= 0.10     -> "↑ RISING"
            meta > synergy && meta > counter -> "◆ META"
            synergy > meta && synergy > counter -> "◈ SYNERGY"
            counter > meta && counter > synergy -> "◉ COUNTER"
            else                             -> "◎ BALANCED"
        }

        val reason = buildReason(candidate, alliedPicks, enemyPicks, missingLanes)

        return HeroScore(candidate, total, meta, synergy, counter, role, badge, reason)
    }

    /**
     * Simple linear scoring formula used by unit tests and lightweight callers.
     */
    fun computeScore(
        hero: Hero,
        allies: List<Hero>,
        enemies: List<Hero>,
        weights: ScoreWeights
    ): Double {
        val meta: Double    = hero.winRate * weights.meta.toDouble()
        val counter: Double = if (enemies.isEmpty()) 0.0
                              else enemies.count { e -> e.id in hero.counters }.toDouble() /
                                   enemies.size * weights.counter.toDouble()
        val synergy: Double = if (allies.isEmpty()) 0.0
                              else allies.count { a -> a.id in hero.synergies }.toDouble() /
                                   allies.size * weights.synergy.toDouble()
        return meta + counter + synergy
    }

    fun rankAll(
        pool: List<Hero>,
        alliedPicks: List<Hero>,
        enemyPicks: List<Hero>,
        bannedIds: Set<Int>,
        weights: ScoreWeights,
        currentTurn: PickTurn?,
        pickIndex: Int = 0,
        maxPickIndex: Int = 10,
        poolMap: Map<Int, Proficiency> = emptyMap()
    ): List<HeroScore> {
        val missingLanes = CompositionAnalyzer.getMissingLanes(alliedPicks)
        val available    = pool.filter { it.id !in bannedIds }
        // TD-05: Compute dynamic bounds once for the entire pool, not per-hero.
        val bounds = computeBounds(pool)
        return available
            .map { score(it, alliedPicks, enemyPicks, bannedIds, weights, missingLanes, currentTurn, pickIndex, maxPickIndex, poolMap, bounds) }
            .sortedByDescending { it.totalScore }
    }

    // ── Weight adaptation (Section 3.3.1 — continuous pick-index curve) ───────

    private fun adaptiveWeights(base: ScoreWeights, pickIndex: Int, maxPickIndex: Int): ScoreWeights {
        if (maxPickIndex <= 0) return base
        val t = (pickIndex.toFloat() / maxPickIndex).coerceIn(0f, 1f)
        val boost = 0.15f * t
        val newMeta    = (base.meta    - boost * 2f).coerceAtLeast(0.05f)
        val newSynergy = base.synergy + boost
        val newCounter = base.counter + boost
        // Argument order matches ScoreWeights.normalized(meta, synergy, counter).
        return ScoreWeights.normalized(newMeta, newSynergy, newCounter)
    }

    // ── Component scoring ─────────────────────────────────────────────────────

    /**
     * TD-05: Uses dynamic [ScoreBounds] for normalisation instead of hardcoded
     * constants.  When called with [ScoreBounds.DEFAULT] the behaviour is
     * backward-compatible with the previous fixed-threshold implementation.
     */
    private fun scoreMeta(hero: Hero, bounds: ScoreBounds = ScoreBounds.DEFAULT): Float {
        val winContrib: Float  = ((hero.winRate.toFloat() - bounds.winRateMedian) / bounds.winRateScale)
                                     .coerceIn(-1f, 1f) * 0.5f + 0.5f  // remap [−1,1] → [0,1]
        val banContrib: Float  = (hero.banRate.toFloat()  / bounds.banRateCap).coerceIn(0f, 1f)
        val pickContrib: Float = (hero.pickRate.toFloat() / bounds.pickRateCap).coerceIn(0f, 1f)
        val tierContrib: Float = (1f - hero.tier.order.toFloat() / TIER_MAX_ORDER).coerceIn(0f, 1f)

        val baseScore = winContrib * 0.35f + banContrib * 0.30f + pickContrib * 0.15f + tierContrib * 0.20f

        // Section 4.2.1: Patch velocity multiplier ±15 %.
        val velocityMult = (1f + hero.patchTrend.toFloat().coerceIn(-1.0f, 1.0f) * 0.15f)

        return (baseScore * velocityMult).coerceIn(0f, 1f)
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
        val isRising = hero.patchTrend >= 0.10
        return when {
            synAlly != null && counters.isNotEmpty() ->
                "Synergizes with ${synAlly.name} + counters ${counters.first().name}"
            synAlly != null  -> "Strong combo with ${synAlly.name}"
            counters.isNotEmpty() ->
                "Direct counter to ${counters.take(2).joinToString(", ") { it.name }}"
            hero.lane in missing -> "Fills missing ${hero.lane.display} role"
            isRising && hero.isOP -> "↑ Rising — top meta pick this patch"
            isRising             -> "↑ Rising this patch — %.0f%% win rate".format(hero.winRate * 100)
            hero.isOP            -> "Top meta pick this patch"
            else                 -> "Solid meta choice — %.0f%% win rate".format(hero.winRate * 100)
        }
    }
}
