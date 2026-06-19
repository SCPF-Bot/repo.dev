package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import kotlin.math.roundToInt

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

    private fun calcMetaAdherence(picks: List<Hero>): Float {
        if (picks.isEmpty()) return 0f
        val tierScores = picks.map { 1f - it.tier.order.toFloat() / 4f }
        return tierScores.average().toFloat()
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
