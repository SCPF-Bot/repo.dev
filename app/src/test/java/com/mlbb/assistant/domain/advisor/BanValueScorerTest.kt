package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BanValueScorerTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hero(
        id: Int = 1,
        name: String = "TestHero",
        role: String = "Mage",
        lane: Lane = Lane.MID,
        winRate: Double = 0.50,
        banRate: Double = 0.10,
        isToxicMechanic: Boolean = false,
        isOP: Boolean = false
    ) = Hero(
        id = id, name = name, role = role, secondaryRole = null,
        lane = lane, tier = Tier.A, patchTrend = 0.0,
        winRate = winRate, pickRate = 0.10, banRate = banRate,
        imageUrl = "", counters = emptyList(), counteredBy = emptyList(),
        synergies = emptyList(), recommendedSpells = emptyList(),
        coreItems = emptyList(), flexLanes = emptyList(),
        isToxicMechanic = isToxicMechanic, isOP = isOP
    )

    // ── isAbsoluteBan ─────────────────────────────────────────────────────────

    @Test
    fun `isToxicMechanic triggers absolute ban`() {
        assertTrue(BanValueScorer.isAbsoluteBan(hero(isToxicMechanic = true)))
    }

    @Test
    fun `isOP triggers absolute ban`() {
        assertTrue(BanValueScorer.isAbsoluteBan(hero(isOP = true)))
    }

    @Test
    fun `banRate ≥ 0_40 triggers absolute ban`() {
        assertTrue(BanValueScorer.isAbsoluteBan(hero(banRate = 0.40)))
        assertTrue(BanValueScorer.isAbsoluteBan(hero(banRate = 0.55)))
    }

    @Test
    fun `normal hero is not absolute ban`() {
        assertFalse(BanValueScorer.isAbsoluteBan(hero(banRate = 0.10)))
    }

    // ── score bounds ──────────────────────────────────────────────────────────

    @Test
    fun `score is in 0 to 1 range for baseline hero`() {
        val s = BanValueScorer.score(hero())
        assertTrue(s >= 0f && s <= 1f)
    }

    @Test
    fun `toxic hero scores higher than baseline hero`() {
        val base  = BanValueScorer.score(hero())
        val toxic = BanValueScorer.score(hero(isToxicMechanic = true))
        assertTrue(toxic > base)
    }

    @Test
    fun `high ban rate hero scores higher than low ban rate`() {
        val low  = BanValueScorer.score(hero(banRate = 0.05))
        val high = BanValueScorer.score(hero(banRate = 0.50))
        assertTrue(high > low)
    }

    @Test
    fun `lane bonus applies when hero lane is in priority list`() {
        val noBonus   = BanValueScorer.score(hero(lane = Lane.MID), priorityLanes = listOf("Gold"))
        val withBonus = BanValueScorer.score(hero(lane = Lane.MID), priorityLanes = listOf("MID"))
        assertTrue(withBonus >= noBonus)
    }

    @Test
    fun `score is clamped to 1_0f even for extreme inputs`() {
        val maxHero = hero(winRate = 1.0, banRate = 1.0, isToxicMechanic = true, isOP = true)
        assertEquals(1.0f, BanValueScorer.score(maxHero), 0.001f)
    }
}
