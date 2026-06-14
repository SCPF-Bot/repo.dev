package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane

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

    private val magicRoles  = setOf("Mage", "Support")
    private val highMobRoles = setOf("Assassin")
    private val sustainRoles = setOf("Support", "Fighter")
    private val highCCRoles  = setOf("Tank", "Support")

    private val ccHeroes = setOf(
        "Tigreal", "Atlas", "Khufra", "Franco", "Johnson",
        "Chou", "Jawhead", "Kaja", "Aurora", "Selena"
    )

    fun analyze(heroes: List<Hero>): CompositionProfile {
        if (heroes.isEmpty()) return CompositionProfile(0f, 0f, CCLevel.NONE, MobilityLevel.LOW, SustainLevel.LOW, emptyList())

        val magicCount    = heroes.count { it.role in magicRoles }
        val physicalCount = heroes.size - magicCount
        val magicPct      = magicCount.toFloat()  / heroes.size
        val physicalPct   = physicalCount.toFloat()/ heroes.size

        val ccCount       = heroes.count { it.name in ccHeroes || it.role in highCCRoles }
        val mobCount      = heroes.count { it.role in highMobRoles }
        val sustainCount  = heroes.count { it.role in sustainRoles }

        val ccLevel       = when {
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
            if (physicalPct >= 0.80f) add("⚠️ Full physical — one Dominance Ice counters team")
            if (magicPct    >= 0.80f) add("⚠️ Full magic — build Oracle to counter")
            if (ccLevel == CCLevel.NONE) add("⚠️ No CC — dive compositions will dominate")
            if (sustainLevel == SustainLevel.LOW) add("⚠️ Low sustain — avoid extended fights")
            if (mobilityLevel == MobilityLevel.HIGH) add("⚠️ High enemy mobility — bring crowd control")
        }

        return CompositionProfile(physicalPct, magicPct, ccLevel, mobilityLevel, sustainLevel, warnings)
    }

    fun getLanesFilled(picks: List<Hero>): Map<Lane, Hero?> {
        val result = Lane.entries.associateWith<Lane, Hero?> { null }.toMutableMap()
        val assigned = mutableSetOf<Int>()
        picks.filterNotNull().forEach { hero ->
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

    fun generateStrengths(profile: CompositionProfile): List<String> = buildList {
        if (profile.ccLevel == CCLevel.HIGH)         add("✅ Strong CC chain")
        if (profile.sustainLevel == SustainLevel.HIGH) add("✅ Excellent team sustain")
        if (profile.mobilityLevel == MobilityLevel.HIGH) add("✅ High mobility — great rotations")
        if (profile.magicPct > 0.4f && profile.physicalPct > 0.4f) add("✅ Mixed damage — hard to itemize against")
    }

    fun generateWeaknesses(profile: CompositionProfile): List<String> = buildList {
        if (profile.ccLevel     == CCLevel.NONE)      add("⚠️ Lack of CC")
        if (profile.sustainLevel == SustainLevel.LOW) add("⚠️ Squishy lineup")
        if (profile.physicalPct >= 0.80f)             add("⚠️ Full physical damage")
        if (profile.magicPct    >= 0.80f)             add("⚠️ Full magic damage")
    }
}
