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
     * ## A4 — Ban value vs ban urgency separation (roadmap A4, now implemented)
     *
     * The ban decision is split into two orthogonal dimensions:
     *
     * - **Value** ([BanValueScorer]): intrinsic, context-free desirability of
     *   removing a hero from the pool.  Driven by win rate, ban rate, toxic
     *   mechanics, and OP status.
     *
     * - **Urgency** ([BanUrgencyScorer]): reactive, context-sensitive priority
     *   of banning a hero *right now* given the current draft state.  Driven by
     *   counter threats to allied picks, synergy with enemy picks, and archetype
     *   enabling.
     *
     * Final score = value + urgency, clamped to [0, 1].
     *
     * Absolute bans (Section 3.3.3):
     *   isToxicMechanic = true  OR  isOP = true  OR  banRate ≥ 0.40
     * Any single flag is sufficient.
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

        // Infer enemy archetype for urgency calculations (may be null with < 2 picks).
        // enemyProfile is computed once and reused for enemyArchetype via let — this
        // eliminates the previous `enemyProfile!!` non-null assert (L-02) which, while
        // technically safe behind the size guard, was misleading to readers.
        val enemyProfile = if (enemyPicks.size >= 2) CompositionAnalyzer.analyze(enemyPicks) else null
        val enemyArchetype = enemyProfile?.let { profile ->
            val roles    = enemyPicks.map { it.role }
            val ccUltCnt = enemyPicks.count { it.hasCCUlt }
            CompositionArchetype.detect(profile, roles, ccUltCnt)
        }

        val scored = pool.map { hero ->
            // A4: Split into value (context-free) + urgency (context-sensitive)
            val value   = BanValueScorer.score(hero, preferredLanes)
            val urgency = BanUrgencyScorer.score(hero, alliedPicks, enemyPicks, enemyArchetype)
            val totalScore = (value + urgency).coerceIn(0f, 1f)

            val isAbsolute = BanValueScorer.isAbsoluteBan(hero)
            val isReactive = alliedPicks.any { ally -> hero.id in ally.counteredBy } ||
                             enemyPicks.any  { ep   -> hero.id in ep.synergies }

            val category = when {
                isAbsolute -> BanCategory.ABSOLUTE
                isReactive -> BanCategory.REACTIVE
                else       -> BanCategory.SITUATIONAL
            }

            BanSuggestion(
                hero       = hero,
                score      = totalScore,
                reason     = buildBanReason(hero, alliedPicks, enemyPicks),
                badgeLabel = when {
                    hero.isToxicMechanic                      -> "Toxic"
                    hero.isOP                                 -> "OP Meta"
                    hero.banRate > BanThresholds.HIGH_BAN_RATE -> "High Ban"
                    isReactive                                -> "Counter Ban"
                    else                                      -> "Situational"
                },
                category = category
            )
        }.sortedByDescending { it.score }

        val absolute = scored.filter { it.category == BanCategory.ABSOLUTE }.take(3)
        val reactive = scored.filter { it.category == BanCategory.REACTIVE }.take(3)

        return BanPriorities(absolute, reactive)
    }

    private fun buildBanReason(hero: Hero, alliedPicks: List<Hero>, enemyPicks: List<Hero>): String {
        // Prefer urgency-derived reason if available
        val urgencyReason = BanUrgencyScorer.buildReason(hero, alliedPicks, enemyPicks)
        if (urgencyReason != null) return urgencyReason

        return when {
            hero.isToxicMechanic ->
                "Toxic mechanics — difficult for most players to deal with"
            hero.isOP && hero.banRate > 0.25 ->
                "OP in current meta — %.0f%% ban rate".format(hero.banRate * 100)
            hero.isOP ->
                "Strong pick — %.0f%% win rate this patch".format(hero.winRate * 100)
            hero.banRate >= BanThresholds.CONSENSUS_BAN_RATE ->
                "Community consensus ban — %.0f%% ban rate".format(hero.banRate * 100)
            hero.banRate > BanThresholds.NOTABLE_BAN_RATE ->
                "High community ban pressure — %.0f%% ban rate".format(hero.banRate * 100)
            else ->
                "Counters common team compositions"
        }
    }
}
