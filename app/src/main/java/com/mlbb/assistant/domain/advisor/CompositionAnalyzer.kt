package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.advisor.CompositionArchetype

/**
 * Composition snapshot derived from the current set of heroes.
 *
 * Pure-Kotlin domain data class with val-only fields; the Compose compiler
 * infers stability automatically without a framework annotation.
 */
data class CompositionProfile(
    val physicalPct: Float,
    val magicPct: Float,
    val ccLevel: CCLevel,
    val mobilityLevel: MobilityLevel,
    val sustainLevel: SustainLevel,
    val warnings: List<String>
)

enum class CCLevel    { NONE, LOW, MEDIUM, HIGH }
enum class MobilityLevel { LOW, MEDIUM, HIGH }
enum class SustainLevel  { LOW, MEDIUM, HIGH }

object CompositionAnalyzer {

    private val magicRoles   = setOf("Mage", "Support")
    private val highMobRoles = setOf("Assassin")
    private val sustainRoles = setOf("Support", "Fighter")
    private val highCCRoles  = setOf("Tank", "Support")

    /**
     * P2-01 fix: magic float thresholds extracted into named constants.
     *
     * Previously `0.80f` appeared as a bare literal in [analyze] (warnings)
     * and [generateWeaknesses], and `0.4f` appeared in [generateStrengths],
     * with no indication of what they represented or whether they were the
     * same conceptual value. Named constants clarify intent and make
     * patch-level threshold adjustments a single-place change.
     */
    private object CompThresholds {
        const val DAMAGE_FULL  = 0.80f   // ≥ 80 % single damage type → composition-wide warning
        const val DAMAGE_MIXED = 0.40f   // > 40 % of each type → considered "mixed" damage
    }

    fun analyze(heroes: List<Hero>): CompositionProfile {
        if (heroes.isEmpty()) return CompositionProfile(0f, 0f, CCLevel.NONE, MobilityLevel.LOW, SustainLevel.LOW, emptyList())

        val magicCount    = heroes.count { it.role in magicRoles }
        val physicalCount = heroes.size - magicCount
        val magicPct      = magicCount.toFloat()   / heroes.size
        val physicalPct   = physicalCount.toFloat() / heroes.size

        // TD-01: Use hasCCUlt field instead of a hardcoded name-based CC list.
        val ccCount      = heroes.count { it.hasCCUlt || it.role in highCCRoles }
        val mobCount     = heroes.count { it.role in highMobRoles }
        val sustainCount = heroes.count { it.role in sustainRoles }

        // Gap detection: count tanks/supports (frontline presence)
        val tankCount    = heroes.count { it.role == "Tank" }
        val supportCount = heroes.count { it.role == "Support" }
        val hasFrontline = (tankCount + supportCount) >= 1

        // Gap detection: assassin/marksman count for squishiness
        val glassCannonCount = heroes.count { it.role in setOf("Assassin", "Marksman") }

        val ccLevel = when {
            ccCount >= 3 -> CCLevel.HIGH
            ccCount == 2 -> CCLevel.MEDIUM
            ccCount == 1 -> CCLevel.LOW
            else         -> CCLevel.NONE
        }
        val mobilityLevel = when {
            mobCount >= 3 -> MobilityLevel.HIGH
            mobCount >= 1 -> MobilityLevel.MEDIUM
            else          -> MobilityLevel.LOW
        }
        val sustainLevel = when {
            sustainCount >= 3 -> SustainLevel.HIGH
            sustainCount >= 1 -> SustainLevel.MEDIUM
            else              -> SustainLevel.LOW
        }

        val warnings = buildList {
            if (physicalPct >= CompThresholds.DAMAGE_FULL) add("⚠️ Full physical — one Dominance Ice counters team")
            if (magicPct    >= CompThresholds.DAMAGE_FULL) add("⚠️ Full magic — build Oracle to counter")
            if (ccLevel == CCLevel.NONE) add("⚠️ No CC — dive compositions will dominate")
            if (sustainLevel == SustainLevel.LOW) add("⚠️ Low sustain — avoid extended fights")
            if (mobilityLevel == MobilityLevel.HIGH) add("⚠️ High enemy mobility — bring crowd control")
            // Archetype gap warnings (ported from AlanNobita scoring.py ally-state detection)
            if (magicCount == 0 && heroes.size >= 2) add("⚠️ No magic damage — enemy will rush physical defense")
            if (!hasFrontline && glassCannonCount >= 2) add("⚠️ Squishy comp — add a tank or support to survive dives")
        }

        return CompositionProfile(physicalPct, magicPct, ccLevel, mobilityLevel, sustainLevel, warnings)
    }

    /**
     * M-01 fix: assigns heroes to lanes in order of *constraint*, not pick order.
     *
     * Previously this walked [picks] in draft order and greedily claimed each
     * hero's preferred lane. When two heroes both preferred the same lane, the
     * one picked earlier always won it — even if the earlier hero had other
     * flexible lanes available and the later hero had none. That's a classic
     * greedy-order bias: a flexible hero could permanently starve an inflexible
     * one out of its only viable lane.
     *
     * Sorting by ascending flexibility (fewest viable lanes first, i.e.
     * `1 + flexLanes.size`) assigns the most-constrained heroes first, so a
     * hero with no flex options gets first claim on its only lane before a
     * flexible hero (which has fallback options) can take it. Ties keep the
     * original pick order via a stable sort on index.
     */
    fun getLanesFilled(picks: List<Hero>): Map<Lane, Hero?> {
        val result   = Lane.entries.associateWith<Lane, Hero?> { null }.toMutableMap()
        val assigned = mutableSetOf<Int>()

        val byConstraint = picks.withIndex().sortedWith(
            compareBy({ (_, hero) -> 1 + hero.flexLanes.size }, { (index, _) -> index })
        )

        byConstraint.forEach { (_, hero) ->
            if (hero.id !in assigned) {
                val preferred = hero.lane
                if (result[preferred] == null) {
                    result[preferred] = hero
                    assigned.add(hero.id)
                } else {
                    hero.flexLanes.firstOrNull { result[it] == null }?.let {
                        result[it] = hero
                        assigned.add(hero.id)
                    }
                }
            }
        }
        return result
    }

    fun getMissingLanes(picks: List<Hero>): List<Lane> {
        val filled = getLanesFilled(picks)
        return Lane.entries.filter { filled[it] == null }
    }

    // ── Archetype detection (Section 3.3.2) ───────────────────────────────────

    /**
     * Detects the primary [CompositionArchetype] for [heroes].
     * Requires at least 2 heroes for a meaningful result.
     */
    fun detectArchetype(heroes: List<Hero>): CompositionArchetype {
        if (heroes.size < 2) return CompositionArchetype.BALANCED
        val profile   = analyze(heroes)
        val roles     = heroes.map { it.role }
        val ccUltCnt  = heroes.count { it.hasCCUlt }
        return CompositionArchetype.detect(profile, roles, ccUltCnt)
    }

    fun generateStrengths(profile: CompositionProfile): List<String> = buildList {
        if (profile.ccLevel == CCLevel.HIGH)             add("✅ Strong CC chain")
        if (profile.sustainLevel == SustainLevel.HIGH)   add("✅ Excellent team sustain")
        if (profile.mobilityLevel == MobilityLevel.HIGH) add("✅ High mobility — great rotations")
        if (profile.magicPct > CompThresholds.DAMAGE_MIXED && profile.physicalPct > CompThresholds.DAMAGE_MIXED)
            add("✅ Mixed damage — hard to itemize against")
    }

    fun generateWeaknesses(profile: CompositionProfile): List<String> = buildList {
        if (profile.ccLevel      == CCLevel.NONE)             add("⚠️ Lack of CC")
        if (profile.sustainLevel == SustainLevel.LOW)         add("⚠️ Squishy lineup")
        if (profile.physicalPct  >= CompThresholds.DAMAGE_FULL) add("⚠️ Full physical damage")
        if (profile.magicPct     >= CompThresholds.DAMAGE_FULL) add("⚠️ Full magic damage")
    }

    /**
     * Returns warning strings for each of [ourPicks] that is countered by at least
     * one hero in [enemyPicks].  Used by the draft screen to surface counter-pick
     * risk in real time.
     *
     * Example: "⚠️ Layla is countered by Saber (enemy pick)"
     */
    fun getCounterPickWarnings(ourPicks: List<Hero>, enemyPicks: List<Hero>): List<String> {
        if (ourPicks.isEmpty() || enemyPicks.isEmpty()) return emptyList()
        return buildList {
            ourPicks.forEach { ally ->
                val counters = enemyPicks.filter { enemy -> ally.id in enemy.counters }
                if (counters.isNotEmpty()) {
                    val names = counters.take(2).joinToString(", ") { it.name }
                    add("⚠️ ${ally.name} is countered by $names")
                }
            }
        }
    }
}
