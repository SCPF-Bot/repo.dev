package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights

/**
 * Category of a ban recommendation (Section 3.3.3 — absolute vs. reactive).
 *
 * - ABSOLUTE: heroes that should be banned regardless of enemy composition
 *   (OP/toxic and community-consensus bans).
 * - REACTIVE: heroes whose ban priority is elevated because they counter the
 *   friendly team's picks or amplify the enemy's inferred archetype.
 * - SITUATIONAL: moderate threat, worth banning if absolute/reactive slots
 *   are exhausted.
 */
enum class BanCategory(val display: String) {
    ABSOLUTE("Must Ban"),
    REACTIVE("Counter Ban"),
    SITUATIONAL("Situational")
}

/**
 * Recommendation returned by [BanRecommender].
 *
 * Pure-Kotlin domain data class; Compose compiler infers stability from
 * val-only fields without a framework annotation.
 */
data class BanSuggestion(
    val hero: Hero,
    val score: Float,
    val reason: String,
    val badgeLabel: String,       // "OP Meta" | "Toxic" | "Counter" | "High Ban"
    val category: BanCategory = BanCategory.SITUATIONAL
)

/**
 * Split result of [BanRecommender.rankSplit].
 *
 * @param absolute  Heroes that must always be banned (OP/toxic/consensus).
 * @param reactive  Heroes whose ban is specifically triggered by the friendly
 *                  or enemy composition context.
 */
data class BanPriorities(
    val absolute: List<BanSuggestion>,
    val reactive: List<BanSuggestion>
) {
    /** Flat list for callers that don't need the split (backward-compat). */
    val all: List<BanSuggestion> get() = (absolute + reactive).sortedByDescending { it.score }
}

object BanRecommender {

    /**
     * Returns a single flat ranked list (backward-compatible).
     * Internally delegates to [rankSplit].
     */
    fun rank(
        availableHeroes: List<Hero>,
        bannedIds: Set<Int>,
        pickedIds: Set<Int>,
        weights: ScoreWeights,
        preferredLanes: List<String> = emptyList()
    ): List<BanSuggestion> = rankSplit(
        availableHeroes, bannedIds, pickedIds, weights, preferredLanes,
        alliedPicks = emptyList(), enemyPicks = emptyList()
    ).all.take(3)

    /**
     * Returns absolute and reactive ban lists separately.
     *
     * Absolute bans (Section 3.3.3):
     *   isToxicMechanic = true  OR  isOP = true  OR  banRate ≥ 0.40
     * Any single flag is sufficient — the previous code erroneously required
     * BOTH isOP AND isToxicMechanic, which under-flagged many must-ban heroes.
     *
     * Reactive bans are context-sensitive: they counter allied picks or strengthen
     * the enemy's inferred composition archetype.
     */
    fun rankSplit(
        availableHeroes: List<Hero>,
        bannedIds: Set<Int>,
        pickedIds: Set<Int>,
        weights: ScoreWeights,
        preferredLanes: List<String> = emptyList(),
        alliedPicks: List<Hero> = emptyList(),
        enemyPicks: List<Hero> = emptyList()
    ): BanPriorities {

        val pool = availableHeroes.filter { it.id !in bannedIds && it.id !in pickedIds }

        // Absolute ban criterion: any one flag is sufficient.
        val isAbsolute: (Hero) -> Boolean = { hero ->
            hero.isToxicMechanic ||
            hero.isOP            ||
            hero.banRate >= 0.40
        }

        // Reactive: directly counters one of our picks, or synergises with an enemy pick.
        val isReactive: (Hero) -> Boolean = { hero ->
            alliedPicks.any { ally -> hero.id in ally.counteredBy } ||
            enemyPicks.any  { ep   -> hero.id in ep.synergies }
        }

        val scored = pool.map { hero ->
            val baseScore     = computeBaseScore(hero, preferredLanes)
            val reactiveBonus = if (isReactive(hero)) 0.20f else 0f
            val totalScore    = (baseScore + reactiveBonus).coerceIn(0f, 1f)

            val category = when {
                isAbsolute(hero) -> BanCategory.ABSOLUTE
                isReactive(hero) -> BanCategory.REACTIVE
                else             -> BanCategory.SITUATIONAL
            }

            BanSuggestion(
                hero       = hero,
                score      = totalScore,
                reason     = buildBanReason(hero, alliedPicks, enemyPicks),
                badgeLabel = when {
                    hero.isToxicMechanic -> "Toxic"
                    hero.isOP            -> "OP Meta"
                    hero.banRate > 0.25  -> "High Ban"
                    reactiveBonus > 0f   -> "Counter Ban"
                    else                 -> "Situational"
                },
                category = category
            )
        }.sortedByDescending { it.score }

        val absolute = scored.filter { it.category == BanCategory.ABSOLUTE }.take(3)
        val reactive = scored.filter { it.category == BanCategory.REACTIVE }.take(3)

        return BanPriorities(absolute, reactive)
    }

    private fun computeBaseScore(hero: Hero, preferredLanes: List<String>): Float {
        val metaScore:  Float = (hero.winRate.toFloat() - 0.50f).coerceAtLeast(0f) * 2f +
                                 hero.banRate.toFloat() * 1.5f
        val toxicBonus: Float = if (hero.isToxicMechanic) 0.30f else 0f
        val opBonus:    Float = if (hero.isOP) 0.25f else 0f
        val laneBonus:  Float = if (hero.lane.name in preferredLanes) 0.10f else 0f
        return (metaScore + toxicBonus + opBonus + laneBonus).coerceIn(0f, 1f)
    }

    private fun buildBanReason(hero: Hero, alliedPicks: List<Hero>, enemyPicks: List<Hero>): String {
        val counteringAlly = alliedPicks.firstOrNull { ally -> hero.id in ally.counteredBy }
        val synergyEnemy   = enemyPicks.firstOrNull  { ep   -> hero.id in ep.synergies }
        return when {
            counteringAlly != null ->
                "Directly counters your ${counteringAlly.name} — reactive ban priority"
            synergyEnemy != null ->
                "Synergizes with enemy ${synergyEnemy.name} — deny the combo"
            hero.isToxicMechanic ->
                "Toxic mechanics — difficult for most players to deal with"
            hero.isOP && hero.banRate > 0.25 ->
                "OP in current meta — %.0f%% ban rate".format(hero.banRate * 100)
            hero.isOP ->
                "Strong pick — %.0f%% win rate this patch".format(hero.winRate * 100)
            hero.banRate >= 0.40 ->
                "Community consensus ban — %.0f%% ban rate".format(hero.banRate * 100)
            hero.banRate > 0.20 ->
                "High community ban pressure — %.0f%% ban rate".format(hero.banRate * 100)
            else ->
                "Counters common team compositions"
        }
    }
}
