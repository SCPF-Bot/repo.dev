package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CompositionAnalyzer].
 * Covers analyze(), getLanesFilled(), getMissingLanes(), generateStrengths(), generateWeaknesses().
 */
class CompositionAnalyzerTest {

    private fun hero(
        id: Int,
        name: String = "Hero$id",
        role: String = "Fighter",
        lane: Lane = Lane.GOLD,
        flexLanes: List<Lane> = emptyList()
    ) = Hero(
        id               = id,
        name             = name,
        role             = role,
        secondaryRole    = null,
        lane             = lane,
        tier             = Tier.B,
        patchTrend       = 0.0,
        winRate          = 0.50,
        pickRate         = 0.10,
        banRate          = 0.05,
        imageUrl         = "",
        counters         = emptyList(),
        counteredBy      = emptyList(),
        synergies        = emptyList(),
        recommendedSpells = emptyList(),
        coreItems        = emptyList(),
        flexLanes        = flexLanes,
        isToxicMechanic  = false,
        isOP             = false
    )

    // ── analyze(): empty ─────────────────────────────────────────────────────

    @Test
    fun `empty list produces baseline profile with all low levels`() {
        val profile = CompositionAnalyzer.analyze(emptyList())
        assertEquals(0f, profile.physicalPct, 0f)
        assertEquals(0f, profile.magicPct, 0f)
        assertEquals(CCLevel.NONE, profile.ccLevel)
        assertEquals(MobilityLevel.LOW, profile.mobilityLevel)
        assertEquals(SustainLevel.LOW, profile.sustainLevel)
        assertTrue(profile.warnings.isEmpty())
    }

    // ── analyze(): damage split ───────────────────────────────────────────────

    @Test
    fun `full physical team triggers Dominance Ice warning`() {
        val heroes = listOf(
            hero(1, role = "Fighter"),
            hero(2, role = "Fighter"),
            hero(3, role = "Marksman"),
            hero(4, role = "Assassin"),
            hero(5, role = "Fighter")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        assertTrue(profile.physicalPct >= 0.80f)
        assertTrue(profile.warnings.any { "Dominance Ice" in it })
    }

    @Test
    fun `full magic team triggers Oracle warning`() {
        val heroes = listOf(
            hero(1, role = "Mage"),
            hero(2, role = "Mage"),
            hero(3, role = "Support"),
            hero(4, role = "Mage"),
            hero(5, role = "Mage")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        assertTrue(profile.magicPct >= 0.80f)
        assertTrue(profile.warnings.any { "Oracle" in it })
    }

    @Test
    fun `mixed damage team produces no damage-type warning`() {
        val heroes = listOf(
            hero(1, role = "Mage"),
            hero(2, role = "Fighter"),
            hero(3, role = "Marksman"),
            hero(4, role = "Support"),
            hero(5, role = "Tank")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        assertFalse(profile.warnings.any { "Dominance Ice" in it })
        assertFalse(profile.warnings.any { "Oracle" in it })
    }

    // ── analyze(): CC levels ─────────────────────────────────────────────────

    @Test
    fun `no CC heroes produces NONE cc level and no-CC warning`() {
        val heroes = listOf(
            hero(1, role = "Fighter"),
            hero(2, role = "Mage"),
            hero(3, role = "Marksman")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        assertEquals(CCLevel.NONE, profile.ccLevel)
        assertTrue(profile.warnings.any { "CC" in it || "cc" in it.lowercase() })
    }

    @Test
    fun `three tank or support heroes produce HIGH cc level`() {
        val heroes = listOf(
            hero(1, role = "Tank"),
            hero(2, role = "Tank"),
            hero(3, role = "Support"),
            hero(4, role = "Fighter"),
            hero(5, role = "Marksman")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        assertEquals(CCLevel.HIGH, profile.ccLevel)
    }

    // ── analyze(): named CC heroes ────────────────────────────────────────────

    @Test
    fun `Tigreal counts toward CC even with non-Tank role label`() {
        val heroes = listOf(
            hero(1, name = "Tigreal", role = "Fighter"),   // name-match override
            hero(2, role = "Mage"),
            hero(3, role = "Marksman")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        assertTrue(profile.ccLevel != CCLevel.NONE)
    }

    // ── analyze(): mobility ──────────────────────────────────────────────────

    @Test
    fun `three assassins produce HIGH mobility level`() {
        val heroes = (1..3).map { hero(it, role = "Assassin") } +
                     listOf(hero(4, role = "Fighter"), hero(5, role = "Mage"))
        val profile = CompositionAnalyzer.analyze(heroes)
        assertEquals(MobilityLevel.HIGH, profile.mobilityLevel)
        assertTrue(profile.warnings.any { "mobility" in it.lowercase() })
    }

    // ── getLanesFilled / getMissingLanes ──────────────────────────────────────

    @Test
    fun `five heroes each in a unique lane fills all lanes`() {
        val heroes = Lane.entries.mapIndexed { i, lane -> hero(i, lane = lane) }
        val filled = CompositionAnalyzer.getLanesFilled(heroes)
        assertTrue(filled.values.none { it == null })
        val missing = CompositionAnalyzer.getMissingLanes(heroes)
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `single hero leaves four lanes missing`() {
        val hero = hero(1, lane = Lane.MID)
        val missing = CompositionAnalyzer.getMissingLanes(listOf(hero))
        assertEquals(Lane.entries.size - 1, missing.size)
        assertFalse(Lane.MID in missing)
    }

    @Test
    fun `flex lane hero fills preferred lane when available`() {
        val h = hero(1, lane = Lane.MID, flexLanes = listOf(Lane.GOLD))
        val filled = CompositionAnalyzer.getLanesFilled(listOf(h))
        assertEquals(h, filled[Lane.MID])
        assertEquals(null, filled[Lane.GOLD])
    }

    @Test
    fun `flex lane hero moves to flex lane when preferred is occupied`() {
        val h1 = hero(1, lane = Lane.MID)
        val h2 = hero(2, lane = Lane.MID, flexLanes = listOf(Lane.GOLD))
        val filled = CompositionAnalyzer.getLanesFilled(listOf(h1, h2))
        assertEquals(h1, filled[Lane.MID])
        assertEquals(h2, filled[Lane.GOLD])
    }

    // ── generateStrengths / generateWeaknesses ────────────────────────────────

    @Test
    fun `high CC profile generates CC strength`() {
        val heroes = (1..4).map { hero(it, role = "Tank") }
        val profile = CompositionAnalyzer.analyze(heroes)
        val strengths = CompositionAnalyzer.generateStrengths(profile)
        assertTrue(strengths.any { "CC" in it || "cc" in it.lowercase() })
    }

    @Test
    fun `full physical damage profile generates weakness entry`() {
        val heroes = (1..5).map { hero(it, role = "Fighter") }
        val profile = CompositionAnalyzer.analyze(heroes)
        val weaknesses = CompositionAnalyzer.generateWeaknesses(profile)
        assertTrue(weaknesses.any { "physical" in it.lowercase() })
    }

    @Test
    fun `mixed damage profile generates strength entry`() {
        val heroes = listOf(
            hero(1, role = "Mage"),
            hero(2, role = "Mage"),
            hero(3, role = "Fighter"),
            hero(4, role = "Fighter"),
            hero(5, role = "Tank")
        )
        val profile = CompositionAnalyzer.analyze(heroes)
        val strengths = CompositionAnalyzer.generateStrengths(profile)
        assertTrue(strengths.any { "itemize" in it.lowercase() })
    }
}
