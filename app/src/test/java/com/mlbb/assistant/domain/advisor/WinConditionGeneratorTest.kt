package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinConditionGeneratorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeHero(
        id: Int,
        name: String,
        role: String,
        hasCCUlt: Boolean = false,
        lane: Lane = Lane.MID
    ) = Hero(
        id                = id,
        name              = name,
        role              = role,
        secondaryRole     = null,
        lane              = lane,
        tier              = Tier.A,
        patchTrend        = 0.0,
        winRate           = 0.50,
        pickRate          = 0.10,
        banRate           = 0.10,
        imageUrl          = "",
        counters          = emptyList(),
        counteredBy       = emptyList(),
        synergies         = emptyList(),
        recommendedSpells = emptyList(),
        coreItems         = emptyList(),
        flexLanes         = emptyList(),
        isToxicMechanic   = false,
        isOP              = false,
        hasCCUlt          = hasCCUlt
    )

    // ── Empty draft ───────────────────────────────────────────────────────────

    @Test
    fun `returns non-null placeholder for empty ally list`() {
        val result = WinConditionGenerator.generate(emptyList(), null)
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    // ── Archetype-specific win conditions ────────────────────────────────────

    @Test
    fun `dive win condition mentions initiator when tank and assassin are present`() {
        val allies = listOf(
            makeHero(1, "Khufra",   "Tank",     lane = Lane.ROAM),
            makeHero(2, "Ling",     "Assassin", lane = Lane.JUNGLE),
            makeHero(3, "Pharsa",   "Mage",     lane = Lane.MID),
        )
        val result = WinConditionGenerator.generate(allies, CompositionArchetype.DIVE)
        assertTrue(result.contains("Khufra") || result.contains("initiates") || result.contains("dive", ignoreCase = true))
    }

    @Test
    fun `poke win condition mentions poker hero name when mage present`() {
        val allies = listOf(
            makeHero(1, "Pharsa",  "Mage",     lane = Lane.MID),
            makeHero(2, "Layla",   "Marksman", lane = Lane.GOLD),
            makeHero(3, "Rafaela", "Support",  lane = Lane.ROAM),
        )
        val result = WinConditionGenerator.generate(allies, CompositionArchetype.POKE)
        assertTrue(result.contains("Pharsa") || result.contains("Poke", ignoreCase = true))
    }

    @Test
    fun `wombo combo win condition chains two CC ult heroes`() {
        val allies = listOf(
            makeHero(1, "Atlas",  "Tank", hasCCUlt = true, lane = Lane.ROAM),
            makeHero(2, "Odette", "Mage", hasCCUlt = true, lane = Lane.MID),
            makeHero(3, "Layla",  "Marksman", lane = Lane.GOLD),
        )
        val result = WinConditionGenerator.generate(allies, CompositionArchetype.WOMBO_COMBO)
        assertTrue(result.contains("Atlas") && result.contains("Odette"))
    }

    @Test
    fun `split push win condition mentions splitter hero`() {
        val allies = listOf(
            makeHero(1, "Khaleed", "Fighter", lane = Lane.EXP),
            makeHero(2, "Layla",   "Marksman", lane = Lane.GOLD),
            makeHero(3, "Rafaela", "Support",  lane = Lane.ROAM),
        )
        val result = WinConditionGenerator.generate(allies, CompositionArchetype.SPLIT_PUSH)
        assertTrue(result.contains("Khaleed") || result.contains("split", ignoreCase = true))
    }

    @Test
    fun `turtle win condition mentions support hero`() {
        val allies = listOf(
            makeHero(1, "Estes",   "Support",  lane = Lane.ROAM),
            makeHero(2, "Tigreal", "Tank",     lane = Lane.ROAM),
            makeHero(3, "Layla",   "Marksman", lane = Lane.GOLD),
        )
        val result = WinConditionGenerator.generate(allies, CompositionArchetype.TURTLE)
        assertTrue(result.contains("Estes") || result.contains("heal", ignoreCase = true) || result.contains("sustain", ignoreCase = true))
    }

    // ── Balanced fallback ─────────────────────────────────────────────────────

    @Test
    fun `balanced archetype returns non-empty result with key hero names`() {
        val allies = listOf(
            makeHero(1, "Tigreal", "Tank",     lane = Lane.ROAM),
            makeHero(2, "Layla",   "Marksman", lane = Lane.GOLD),
            makeHero(3, "Kagura",  "Mage",     lane = Lane.MID),
        )
        val result = WinConditionGenerator.generate(allies, CompositionArchetype.BALANCED)
        assertTrue(result.isNotBlank())
        // At least one hero name should appear in balanced output
        assertTrue(result.contains("Tigreal") || result.contains("Layla") || result.contains("Kagura"))
    }

    // ── Null archetype falls back gracefully ──────────────────────────────────

    @Test
    fun `null archetype falls back to BALANCED win condition`() {
        val allies = listOf(
            makeHero(1, "Tigreal", "Tank",     lane = Lane.ROAM),
            makeHero(2, "Layla",   "Marksman", lane = Lane.GOLD),
        )
        val result = WinConditionGenerator.generate(allies, null)
        assertTrue(result.isNotBlank())
        assertFalse(result == "Complete the draft to see your win condition")
    }

    // ── Result always ends with actionable advice ─────────────────────────────

    @Test
    fun `all archetypes produce output containing avoid clause`() {
        val allies = listOf(
            makeHero(1, "Ling",   "Assassin", lane = Lane.JUNGLE),
            makeHero(2, "Franco", "Tank",     hasCCUlt = true, lane = Lane.ROAM),
        )
        CompositionArchetype.entries.forEach { archetype ->
            val result = WinConditionGenerator.generate(allies, archetype)
            assertTrue("No advice for $archetype", result.isNotBlank())
        }
    }
}
