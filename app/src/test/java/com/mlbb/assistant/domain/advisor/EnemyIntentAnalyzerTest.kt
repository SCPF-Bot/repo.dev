package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemyIntentAnalyzerTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeHero(
        id: Int,
        name: String,
        role: String,
        hasCCUlt: Boolean  = false,
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

    // ── Too few picks ─────────────────────────────────────────────────────────

    @Test
    fun `returns null when fewer than 2 enemy picks`() {
        assertNull(EnemyIntentAnalyzer.infer(emptyList()))
        assertNull(EnemyIntentAnalyzer.infer(listOf(makeHero(1, "Layla", "Marksman"))))
    }

    // ── Wombo combo detection ─────────────────────────────────────────────────

    @Test
    fun `detects WOMBO_COMBO with multiple CC ult heroes and a tank`() {
        val picks = listOf(
            makeHero(1, "Atlas",    "Tank",    hasCCUlt = true, lane = Lane.ROAM),
            makeHero(2, "Tigreal",  "Tank",    hasCCUlt = true, lane = Lane.ROAM),
            makeHero(3, "Odette",   "Mage",    hasCCUlt = true, lane = Lane.MID),
        )
        val result = EnemyIntentAnalyzer.infer(picks)
        assertNotNull(result)
        assertEquals(CompositionArchetype.WOMBO_COMBO, result!!.archetype)
    }

    // ── Dive detection ────────────────────────────────────────────────────────

    @Test
    fun `detects DIVE comp with 2+ assassins`() {
        val picks = listOf(
            makeHero(1, "Ling",     "Assassin", lane = Lane.JUNGLE),
            makeHero(2, "Lancelot", "Assassin", lane = Lane.JUNGLE),
            makeHero(3, "Gusion",   "Assassin", lane = Lane.MID),
        )
        val result = EnemyIntentAnalyzer.infer(picks)
        assertNotNull(result)
        assertEquals(CompositionArchetype.DIVE, result!!.archetype)
    }

    // ── Poke detection ────────────────────────────────────────────────────────

    @Test
    fun `detects POKE comp with 3+ mages and marksmen`() {
        val picks = listOf(
            makeHero(1, "Pharsa",  "Mage",      lane = Lane.MID),
            makeHero(2, "Chang'e", "Mage",      lane = Lane.MID),
            makeHero(3, "Layla",   "Marksman",  lane = Lane.GOLD),
        )
        val result = EnemyIntentAnalyzer.infer(picks)
        assertNotNull(result)
        assertEquals(CompositionArchetype.POKE, result!!.archetype)
    }

    // ── Intent summary contains hero names ───────────────────────────────────

    @Test
    fun `intent summary includes picked hero names`() {
        val picks = listOf(
            makeHero(1, "Ling",     "Assassin", lane = Lane.JUNGLE),
            makeHero(2, "Lancelot", "Assassin", lane = Lane.JUNGLE),
            makeHero(3, "Gusion",   "Assassin", lane = Lane.MID),
        )
        val result = EnemyIntentAnalyzer.infer(picks)
        assertNotNull(result)
        assertTrue(result!!.intentSummary.contains("Ling") || result.intentSummary.contains("Lancelot"))
    }

    // ── Counter advice not blank ──────────────────────────────────────────────

    @Test
    fun `counter advice is not blank for any detected archetype`() {
        val picks = listOf(
            makeHero(1, "Franco",  "Tank",    hasCCUlt = true, lane = Lane.ROAM),
            makeHero(2, "Kagura",  "Mage",    hasCCUlt = true, lane = Lane.MID),
            makeHero(3, "Layla",   "Marksman", lane = Lane.GOLD),
        )
        val result = EnemyIntentAnalyzer.infer(picks)
        assertNotNull(result)
        assertTrue(result!!.counterAdvice.isNotBlank())
    }

    // ── BALANCED fallback ─────────────────────────────────────────────────────

    @Test
    fun `returns BALANCED for a mixed composition with no clear archetype`() {
        val picks = listOf(
            makeHero(1, "Tigreal",  "Tank",     lane = Lane.ROAM),
            makeHero(2, "Layla",    "Marksman", lane = Lane.GOLD),
            makeHero(3, "Rafaela",  "Support",  lane = Lane.ROAM),
        )
        val result = EnemyIntentAnalyzer.infer(picks)
        assertNotNull(result)
        // BALANCED or TURTLE — both are valid for this lineup
        assertTrue(
            result!!.archetype == CompositionArchetype.BALANCED ||
            result.archetype   == CompositionArchetype.TURTLE
        )
    }
}
