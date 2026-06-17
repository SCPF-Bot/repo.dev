package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights

data class BanSuggestion(
    val hero: Hero,
    val score: Float,
    val reason: String,
    val badgeLabel: String  // "OP Meta" | "Toxic" | "Counter" | "High Ban"
)

object BanRecommender {

    fun rank(
        availableHeroes: List<Hero>,
        bannedIds: Set<Int>,
        pickedIds: Set<Int>,
        weights: ScoreWeights,
        preferredLanes: List<String> = emptyList()
    ): List<BanSuggestion> {

        val pool = availableHeroes.filter { it.id !in bannedIds && it.id !in pickedIds }

        return pool.map { hero ->
            // hero.winRate and hero.banRate are Double; arithmetic here is Float.
            // Explicit .toFloat() required — Kotlin has no Double ± Float operator (Rule 2).
            val metaScore:  Float = (hero.winRate.toFloat() - 0.50f).coerceAtLeast(0f) * 2f +
                                     hero.banRate.toFloat() * 1.5f
            val toxicBonus: Float = if (hero.isToxicMechanic) 0.30f else 0f
            val opBonus:    Float = if (hero.isOP) 0.25f else 0f
            val laneBonus:  Float = if (hero.lane.name in preferredLanes) 0.10f else 0f

            val totalScore: Float = (metaScore + toxicBonus + opBonus + laneBonus)
                                        .coerceIn(0f, 1f)

            val reason = buildBanReason(hero)
            val badge  = when {
                hero.isToxicMechanic -> "Toxic"
                hero.isOP            -> "OP Meta"
                hero.banRate > 0.25f -> "High Ban"
                else                 -> "Counter"
            }

            BanSuggestion(hero, totalScore, reason, badge)
        }
        .sortedByDescending { it.score }
        .take(3)
    }

    private fun buildBanReason(hero: Hero): String = when {
        hero.isToxicMechanic -> "Toxic mechanics — difficult for most players to deal with"
        hero.isOP && hero.banRate > 0.25f ->
            "OP in current meta — %.0f%% ban rate".format(hero.banRate * 100)
        hero.isOP ->
            "Strong pick — %.0f%% win rate this patch".format(hero.winRate * 100)
        hero.banRate > 0.20f ->
            "Community consensus ban — %.0f%% ban rate".format(hero.banRate * 100)
        else ->
            "Counters common team compositions"
    }
}
