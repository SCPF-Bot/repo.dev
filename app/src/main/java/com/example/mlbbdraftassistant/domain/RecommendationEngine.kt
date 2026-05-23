package com.example.mlbbdraftassistant.domain

import com.example.mlbbdraftassistant.data.model.Hero
import kotlin.math.max

data class Recommendation(
    val hero: Hero,
    val totalScore: Float,
    val synergyScore: Float,
    val counterScore: Float,
    val roleBalanceScore: Float,
    val metaScore: Float,
    val reason: String
)

class RecommendationEngine(
    private val config: ScoringConfig = ScoringConfig.DEFAULT
) {
    /**
     * Returns the top N recommended heroes given the current draft state.
     *
     * @param allies The heroes already picked by your team (0‑4 heroes).
     * @param enemies The heroes already picked by the opposing team (0‑5 heroes).
     * @param availableHeroes The complete list of all heroes from the data source.
     * @param topN Number of recommendations to return (default 5).
     */
    fun recommend(
        allies: List<Hero>,
        enemies: List<Hero>,
        availableHeroes: List<Hero>,
        topN: Int = 5
    ): List<Recommendation> {
        val pickedIds = (allies + enemies).map { it.hero_id }.toSet()

        // Only consider heroes not yet picked
        val candidates = availableHeroes.filter { it.hero_id !in pickedIds }

        // If no allies/enemies provided, fall back to meta only
        if (allies.isEmpty() && enemies.isEmpty()) {
            return candidates
                .sortedByDescending { hero ->
                    ScoreNormalizer.metaNormalised(
                        hero.win_rate ?: 0f,
                        hero.pick_rate ?: 0f,
                        hero.ban_rate ?: 0f
                    )
                }
                .take(topN)
                .map { hero ->
                    val meta = ScoreNormalizer.metaNormalised(
                        hero.win_rate ?: 0f,
                        hero.pick_rate ?: 0f,
                        hero.ban_rate ?: 0f
                    )
                    Recommendation(
                        hero = hero,
                        totalScore = meta,
                        synergyScore = 0f,
                        counterScore = 0f,
                        roleBalanceScore = 1f,
                        metaScore = meta,
                        reason = "High meta (win ${hero.win_rate}%, pick ${hero.pick_rate}%)"
                    )
                }
        }

        val allyCount = allies.size
        val enemyCount = enemies.size

        // Compute raw max possible values for normalisation
        val maxSynergyPerAlly = 5f   // max strength in typical datasets
        val maxCounterPerEnemy = 5f  // same assumption

        val scored = candidates.map { candidate ->
            val synergyRaw = computeSynergyRaw(candidate, allies)
            val counterRaw = computeCounterRaw(candidate, enemies)
            val synergyNorm = if (allyCount > 0) {
                ScoreNormalizer.synergyNormalised(synergyRaw, allyCount, maxSynergyPerAlly)
            } else 0f

            val counterNorm = if (enemyCount > 0) {
                ScoreNormalizer.counterNormalised(counterRaw, enemyCount, maxCounterPerEnemy)
            } else 0f

            // Role balance: check how many current allies share the candidate's role
            val roleCount = allies.count { it.hero_role == candidate.hero_role } + 1 // +1 for candidate itself
            val roleBalance = ScoreNormalizer.roleBalanceNormalised(roleCount, maxAllowed = 2)

            val meta = ScoreNormalizer.metaNormalised(
                candidate.win_rate ?: 0f,
                candidate.pick_rate ?: 0f,
                candidate.ban_rate ?: 0f
            )

            val total = config.synergyWeight * synergyNorm +
                    config.counterWeight * counterNorm +
                    config.roleWeight * roleBalance +
                    config.metaWeight * meta

            val reason = buildReason(
                candidate,
                synergyNorm,
                counterNorm,
                roleBalance,
                meta,
                allies,
                enemies
            )

            Recommendation(
                hero = candidate,
                totalScore = total,
                synergyScore = synergyNorm,
                counterScore = counterNorm,
                roleBalanceScore = roleBalance,
                metaScore = meta,
                reason = reason
            )
        }

        return scored
            .sortedByDescending { it.totalScore }
            .take(topN)
    }

    private fun computeSynergyRaw(candidate: Hero, allies: List<Hero>): Float {
        if (allies.isEmpty()) return 0f
        var sum = 0f
        for (ally in allies) {
            val synergyEntry = candidate.hero_synergies?.find { it.hero_id == ally.hero_id }
            if (synergyEntry != null) {
                sum += synergyEntry.synergy_strength
            }
        }
        return sum
    }

    private fun computeCounterRaw(candidate: Hero, enemies: List<Hero>): Float {
        if (enemies.isEmpty()) return 0f
        var sum = 0f
        for (enemy in enemies) {
            val counterEntry = candidate.hero_counters?.find { it.hero_id == enemy.hero_id }
            if (counterEntry != null) {
                sum += counterEntry.counter_strength
            }
        }
        return sum
    }

    private fun buildReason(
        candidate: Hero,
        synergyNorm: Float,
        counterNorm: Float,
        roleBalance: Float,
        meta: Float,
        allies: List<Hero>,
        enemies: List<Hero>
    ): String {
        val parts = mutableListOf<String>()

        // Counter points
        val counteredEnemies = enemies.filter { enemy ->
            candidate.hero_counters?.any { it.hero_id == enemy.hero_id } == true
        }
        if (counteredEnemies.isNotEmpty()) {
            val names = counteredEnemies.joinToString(", ") { it.hero_name }
            parts.add("Strong vs $names")
        }

        // Synergy points
        val synergizedAllies = allies.filter { ally ->
            candidate.hero_synergies?.any { it.hero_id == ally.hero_id } == true
        }
        if (synergizedAllies.isNotEmpty()) {
            val names = synergizedAllies.joinToString(", ") { it.hero_name }
            parts.add("Good with $names")
        }

        // Meta
        parts.add("Meta: win ${candidate.win_rate}%, pick ${candidate.pick_rate}%")

        // Role balance (only mention if penalty)
        if (roleBalance < 0.8f) {
            parts.add("Role concern (too many ${candidate.hero_role}s)")
        }

        return parts.joinToString(" | ")
    }
}