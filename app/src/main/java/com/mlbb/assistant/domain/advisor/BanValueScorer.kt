package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights

/**
 * Scores how intrinsically valuable a hero is to BAN regardless of context.
 *
 * This is the "value" half of the ban value vs urgency split outlined in
 * roadmap item A4.  It replaces the monolithic [BanRecommender.computeBaseScore]
 * for absolute-ban candidates.
 *
 * ## Formula
 * ```
 * value = winContrib + banContrib + toxicBonus + opBonus + laneBonus
 * ```
 * where each term is clamped and the result is clamped to [0, 1].
 *
 * Value is **context-free**: it does not consider ally picks, enemy picks,
 * or reactive counter relationships.  Context-dependent urgency is handled
 * by [BanUrgencyScorer].
 */
object BanValueScorer {

    private const val WIN_RATE_PIVOT     = 0.50f
    private const val WIN_RATE_SCALE     = 2.0f   // amplify win-rate deviation
    private const val BAN_RATE_SCALE     = 1.5f
    private const val TOXIC_BONUS        = 0.30f
    private const val OP_BONUS           = 0.25f
    private const val LANE_PRIORITY_BONUS = 0.10f

    /**
     * Computes the intrinsic ban value for [hero].
     *
     * @param hero            Hero to evaluate.
     * @param priorityLanes   Lane names that the local team wants to contest
     *                        (e.g. ["Gold", "Jungle"]).  Heroes that play these
     *                        lanes receive a small bonus.
     *
     * @return Ban value score in [0, 1].
     */
    fun score(hero: Hero, priorityLanes: List<String> = emptyList()): Float {
        val winContrib  = (hero.winRate.toFloat() - WIN_RATE_PIVOT).coerceAtLeast(0f) * WIN_RATE_SCALE
        val banContrib  = hero.banRate.toFloat() * BAN_RATE_SCALE
        val toxicBonus  = if (hero.isToxicMechanic) TOXIC_BONUS else 0f
        val opBonus     = if (hero.isOP) OP_BONUS else 0f
        val laneBonus   = if (priorityLanes.any { it.equals(hero.lane.name, ignoreCase = true) })
                              LANE_PRIORITY_BONUS else 0f

        return (winContrib + banContrib + toxicBonus + opBonus + laneBonus).coerceIn(0f, 1f)
    }

    /**
     * Returns true if [hero] qualifies as an absolute ban based purely on
     * meta/toxicity signals — no composition context needed.
     */
    fun isAbsoluteBan(hero: Hero): Boolean =
        hero.isToxicMechanic || hero.isOP || hero.banRate >= 0.40
}
