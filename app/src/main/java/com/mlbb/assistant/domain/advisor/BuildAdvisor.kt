package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.CompositionProfile
import com.mlbb.assistant.domain.model.DamageType
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.HeroScore
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.scoring.ScoreWeights

/**
 * Ranks available heroes for the pick phase given the current draft state.
 *
 * Scoring model (weighted composite):
 *   total = weights.meta    * metaScore
 *         + weights.counter * counterScore
 *         + weights.synergy * synergyScore
 *
 * Where:
 *  - [metaScore]    reflects current-patch tier and win rate (0–1)
 *  - [counterScore] reflects how well the hero counters confirmed enemy picks (0–1)
 *  - [synergyScore] reflects how well the hero synergises with confirmed own picks (0–1)
 *
 * An open-lane bonus (+0.05) is added for heroes whose primary or flex lane
 * fills a gap in the current team composition.
 *
 * Results are sorted descending by total score; top [limit] are returned.
 */
object BuildAdvisor {

    fun rank(
        availableHeroes: List<Hero>,
        bannedIds:        Set<Int>,
        pickedIds:        Set<Int>,
        enemyPickIds:     Set<Int>,
        ourPickIds:       Set<Int>,
        allHeroes:        List<Hero>,
        weights:          ScoreWeights = ScoreWeights.DEFAULT,
        limit:            Int = 5
    ): List<HeroScore> {

        val heroById = allHeroes.associateBy { it.id }
        val enemyHeroes = enemyPickIds.mapNotNull { heroById[it] }
        val ourHeroes   = ourPickIds.mapNotNull   { heroById[it] }
        val composition = analyseComposition(ourHeroes)

        val pool = availableHeroes.filter { it.id !in bannedIds && it.id !in pickedIds }

        return pool.map { hero ->
            val meta    = computeMetaScore(hero)
            val counter = computeCounterScore(hero, enemyHeroes)
            val synergy = computeSynergyScore(hero, ourHeroes)
            val lane    = if (fillsOpenLane(hero, composition)) 0.05f else 0f

            val total = (weights.meta    * meta
                       + weights.counter * counter
                       + weights.synergy * synergy
                       + lane).toDouble().coerceIn(0.0, 1.0)

            val badge  = pickBadge(meta, counter, synergy)
            val reason = buildReason(hero, enemyHeroes, ourHeroes, meta, counter, synergy)

            HeroScore(
                hero         = hero,
                totalScore   = total,
                metaScore    = meta.toDouble(),
                counterScore = counter.toDouble(),
                synergyScore = synergy.toDouble(),
                badgeLabel   = badge,
                reason       = reason
            )
        }
        .sortedByDescending { it.totalScore }
        .take(limit)
    }

    // ── Score helpers ─────────────────────────────────────────────────────────

    /**
     * Meta score: normalised combination of tier rank and win rate.
     *  tier contribution  → mapped from Tier.rank (0 = S+ = best)
     *  winRate contribution → excess above 50% win rate, scaled to [0, 1]
     */
    private fun computeMetaScore(hero: Hero): Float {
        val tierScore = ((5 - hero.tier.rank) / 5f).coerceIn(0f, 1f)
        val winScore  = ((hero.winRate - 0.50) * 2.0).toFloat().coerceAtLeast(0f)
        return (tierScore * 0.6f + winScore * 0.4f).coerceIn(0f, 1f)
    }

    /**
     * Counter score: fraction of confirmed enemy heroes that [hero] counters.
     * Zero if no enemy picks are confirmed yet.
     */
    private fun computeCounterScore(hero: Hero, enemyHeroes: List<Hero>): Float {
        if (enemyHeroes.isEmpty()) return 0f
        val countered = enemyHeroes.count { enemy -> hero.id in enemy.counteredBy }
        return (countered.toFloat() / enemyHeroes.size).coerceIn(0f, 1f)
    }

    /**
     * Synergy score: fraction of confirmed own heroes that synergise with [hero].
     * Zero if no own picks are confirmed yet.
     */
    private fun computeSynergyScore(hero: Hero, ourHeroes: List<Hero>): Float {
        if (ourHeroes.isEmpty()) return 0f
        val synergistic = ourHeroes.count { ally -> hero.id in ally.synergies }
        return (synergistic.toFloat() / ourHeroes.size).coerceIn(0f, 1f)
    }

    /** Returns true if [hero]'s lane (or any flex lane) fills a gap in the composition. */
    private fun fillsOpenLane(hero: Hero, profile: CompositionProfile): Boolean =
        hero.lane in profile.openLanes || hero.flexLanes.any { it in profile.openLanes }

    // ── Composition analysis ──────────────────────────────────────────────────

    private fun analyseComposition(ourHeroes: List<Hero>): CompositionProfile {
        val filledLanes  = ourHeroes.map { it.lane }.toSet()
        val openLanes    = Lane.entries.filter { it !in filledLanes }
        val damageType   = inferDamageType(ourHeroes)

        return CompositionProfile(
            damageType      = damageType,
            hasCrowdControl = ourHeroes.any { it.role.contains("Tank", true) || it.role.contains("Support", true) },
            hasInitiator    = ourHeroes.any { it.role.contains("Tank", true) },
            hasSustain      = ourHeroes.any { it.role.contains("Support", true) },
            hasSplitPush    = ourHeroes.any { it.lane == Lane.EXP },
            openLanes       = openLanes
        )
    }

    private fun inferDamageType(heroes: List<Hero>): DamageType {
        val physical = heroes.count { it.role.contains("Fighter", true) || it.role.contains("Marksman", true) || it.role.contains("Assassin", true) }
        val magical  = heroes.count { it.role.contains("Mage", true) }
        return when {
            physical >= 3 -> DamageType.PHYSICAL
            magical  >= 3 -> DamageType.MAGICAL
            else           -> DamageType.MIXED
        }
    }

    // ── Badge & reason ────────────────────────────────────────────────────────

    private fun pickBadge(meta: Float, counter: Float, synergy: Float): String = when {
        counter >= 0.6f                  -> "Counter Pick"
        synergy >= 0.6f                  -> "Synergy"
        meta    >= 0.7f                  -> "OP Meta"
        meta    >= 0.5f && counter > 0f  -> "Solid Pick"
        else                             -> "Flex"
    }

    private fun buildReason(
        hero:        Hero,
        enemyHeroes: List<Hero>,
        ourHeroes:   List<Hero>,
        meta:        Float,
        counter:     Float,
        synergy:     Float
    ): String {
        val counteredNames = enemyHeroes.filter { hero.id in it.counteredBy }.joinToString(", ") { it.name }
        val synergisesWith = ourHeroes.filter { hero.id in it.synergies }.joinToString(", ") { it.name }

        return when {
            counter >= 0.5f && counteredNames.isNotEmpty() ->
                "Hard counters $counteredNames"
            synergy >= 0.5f && synergisesWith.isNotEmpty() ->
                "Strong synergy with $synergisesWith"
            meta >= 0.7f ->
                "${hero.tier.label} tier — %.0f%% win rate this patch".format(hero.winRate * 100)
            hero.isOP ->
                "Overpowered pick — %.0f%% win rate".format(hero.winRate * 100)
            else ->
                "Solid ${hero.tier.label}-tier pick for current meta"
        }
    }
}
