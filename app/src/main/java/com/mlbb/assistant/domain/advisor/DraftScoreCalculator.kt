package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.utils.averageOrDefault
import kotlin.math.roundToInt

/**
 * Immutable score snapshot for a completed draft session.
 *
 * Pure-Kotlin domain data class with val-only fields; the Compose compiler
 * infers stability automatically without a framework annotation.
 */
data class FinalDraftScore(
    val overall: Int,           // 0–100
    val metaAdherence: Int,
    val counterEfficiency: Int,
    val synergyStrength: Int,
    val teamStrengths: List<String>,
    val teamWeaknesses: List<String>,
    val enemyThreats: List<String>,
    val damagePhysicalPct: Int,
    val damageMagicPct: Int,
    val ccRating: String,
    val sustainRating: String,
    val mobilityRating: String
)

object DraftScoreCalculator {

    /**
     * Maximum tier order value — used to normalise meta adherence so that
     * Tier.UNKNOWN (order = 5) yields exactly 0.0 instead of the previous
     * -0.25 caused by dividing by the hard-coded constant 4.
     */
    private val TIER_MAX_ORDER: Float = Tier.entries.maxOf { it.order }.toFloat()

    fun calculate(
        ourPicks: List<Hero>,
        enemyPicks: List<Hero>,
        followedRecs: Int,
        totalRecs: Int
    ): FinalDraftScore {

        val ourComp    = CompositionAnalyzer.analyze(ourPicks)
        val enemyComp  = CompositionAnalyzer.analyze(enemyPicks)

        val metaScore    = calcMetaAdherence(ourPicks)
        val counterScore = calcCounterEfficiency(ourPicks, enemyPicks)
        val synergyScore = calcSynergyStrength(ourPicks)
        val recFollowPct = if (totalRecs > 0) followedRecs.toFloat() / totalRecs else 0.5f

        val overall = ((metaScore + counterScore + synergyScore) / 3f * 0.7f +
                        recFollowPct * 0.3f) * 100f

        return FinalDraftScore(
            overall            = overall.roundToInt().coerceIn(0, 100),
            metaAdherence      = (metaScore * 100).roundToInt(),
            counterEfficiency  = (counterScore * 100).roundToInt(),
            synergyStrength    = (synergyScore * 100).roundToInt(),
            teamStrengths      = CompositionAnalyzer.generateStrengths(ourComp),
            teamWeaknesses     = CompositionAnalyzer.generateWeaknesses(ourComp),
            enemyThreats       = generateEnemyThreats(enemyPicks, enemyComp),
            damagePhysicalPct  = (ourComp.physicalPct * 100).roundToInt(),
            damageMagicPct     = (ourComp.magicPct    * 100).roundToInt(),
            ccRating           = ourComp.ccLevel.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            sustainRating      = ourComp.sustainLevel.name.lowercase().replaceFirstChar { it.uppercase() },
            mobilityRating     = ourComp.mobilityLevel.name.lowercase().replaceFirstChar { it.uppercase() }
        )
    }

    /**
     * Normalises tier order against [TIER_MAX_ORDER] so the result is always [0, 1].
     *
     * Bug fixed: the previous constant divisor of 4 caused Tier.B (order = 4) to
     * yield exactly 0.0 and Tier.UNKNOWN (order = 5) to yield −0.25, a negative
     * contribution that silently corrupted the overall draft score.
     *
     * M-08 fix: replaced `Iterable<Float>.average()` (which throws on an empty
     * collection and only works on numeric wrapper types) with the explicit
     * [averageOrDefault] extension, matching the empty-collection guard already
     * present here and avoiding the implicit `Double` boxing `.average()` does
     * internally for `Iterable<Float>` collections.
     */
    private fun calcMetaAdherence(picks: List<Hero>): Float {
        if (picks.isEmpty()) return 0f
        return picks
            .averageOrDefault { 1f - it.tier.order.toFloat() / TIER_MAX_ORDER }
            .toFloat()
            .coerceIn(0f, 1f)
    }

    private fun calcCounterEfficiency(ours: List<Hero>, enemies: List<Hero>): Float {
        if (ours.isEmpty() || enemies.isEmpty()) return 0f
        var counterHits = 0
        ours.forEach { ally ->
            enemies.forEach { enemy ->
                if (enemy.id in ally.counters) counterHits++
            }
        }
        return (counterHits.toFloat() / (ours.size * enemies.size.coerceAtLeast(1))).coerceIn(0f, 1f)
    }

    private fun calcSynergyStrength(picks: List<Hero>): Float {
        if (picks.size < 2) return 0f
        var synergyHits = 0
        var pairs = 0
        for (i in picks.indices) {
            for (j in i + 1 until picks.size) {
                if (picks[j].id in picks[i].synergies ||
                    picks[i].id in picks[j].synergies) synergyHits++
                pairs++
            }
        }
        return if (pairs == 0) 0f else (synergyHits.toFloat() / pairs).coerceIn(0f, 1f)
    }

    private fun generateEnemyThreats(enemies: List<Hero>, comp: CompositionProfile): List<String> =
        buildList {
            if (comp.physicalPct > 0.6f) {
                val top = enemies.filter { it.role == "Marksman" || it.role == "Assassin" }.take(2)
                if (top.isNotEmpty()) add("🔴 High physical burst — ${top.joinToString(", ") { it.name }}")
            }
            if (comp.magicPct > 0.6f) {
                val top = enemies.filter { it.role == "Mage" }.take(2)
                if (top.isNotEmpty()) add("🔴 High magic damage — ${top.joinToString(", ") { it.name }}")
            }
            if (comp.ccLevel == CCLevel.HIGH) add("🟡 Heavy CC chain — stay spread out")
            if (comp.mobilityLevel == MobilityLevel.HIGH) add("🟡 High mobility — ward their jungle")
            if (comp.sustainLevel == SustainLevel.HIGH) add("🟢 Strong sustain — use burst combos")
            if (isEmpty()) add("🟢 No outstanding threats detected")
        }
}
