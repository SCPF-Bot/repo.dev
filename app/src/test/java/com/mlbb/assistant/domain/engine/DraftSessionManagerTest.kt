package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DraftSessionManager].
 *
 * DraftSessionManager is a pure Kotlin singleton — no Android or Coroutines
 * machinery needed. Tests use synchronous state reads on [session.value].
 */
class DraftSessionManagerTest {

    private lateinit var manager: DraftSessionManager

    @Before
    fun setUp() {
        manager = DraftSessionManager()
    }

    private fun hero(id: Int) = Hero(
        id               = id,
        name             = "Hero$id",
        role             = "Fighter",
        secondaryRole    = null,
        lane             = Lane.GOLD,
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
        flexLanes        = emptyList(),
        isToxicMechanic  = false,
        isOP             = false
    )

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial session is in IDLE phase`() {
        assertEquals(DraftPhase.IDLE, manager.session.value.phase)
    }

    @Test
    fun `initial session has empty picks and bans`() {
        val s = manager.session.value
        assertTrue(s.ourPicks.all { it == null })
        assertTrue(s.enemyPicks.all { it == null })
        assertTrue(s.ourBansR1.all { it == null })
        assertTrue(s.enemyBansR1.all { it == null })
    }

    // ── initSession ──────────────────────────────────────────────────────────

    @Test
    fun `initSession transitions to SETUP phase`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        assertEquals(DraftPhase.SETUP, manager.session.value.phase)
    }

    @Test
    fun `initSession stores rank correctly`() {
        manager.initSession(Rank.MYTHIC, ourTeamFirst = false)
        assertEquals(Rank.MYTHIC, manager.session.value.rank)
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    @Test
    fun `startBanPhase transitions to BAN_ROUND_1`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startBanPhase()
        assertEquals(DraftPhase.BAN_ROUND_1, manager.session.value.phase)
    }

    @Test
    fun `startPickPhase transitions to PICK and resets pickIndex`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        val s = manager.session.value
        assertEquals(DraftPhase.PICK, s.phase)
        assertEquals(0, s.currentPickIndex)
    }

    @Test
    fun `startTradingPhase transitions to TRADING`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startTradingPhase()
        assertEquals(DraftPhase.TRADING, manager.session.value.phase)
    }

    @Test
    fun `completeDraft transitions to COMPLETE`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.completeDraft()
        assertEquals(DraftPhase.COMPLETE, manager.session.value.phase)
    }

    // ── Ban recording ────────────────────────────────────────────────────────

    @Test
    fun `recordOurBan fills correct slot in round 1`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        val h = hero(42)
        manager.recordOurBan(h, round = 1, slot = 0)
        assertEquals(h, manager.session.value.ourBansR1[0])
    }

    @Test
    fun `recordEnemyBan fills correct slot in round 1`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        val h = hero(99)
        manager.recordEnemyBan(h, round = 1, slot = 2)
        assertEquals(h, manager.session.value.enemyBansR1[2])
    }

    @Test
    fun `missed ban is recorded as null hero`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.recordEnemyBan(null, round = 1, slot = 1)
        assertNull(manager.session.value.enemyBansR1[1])
    }

    // ── Pick recording ────────────────────────────────────────────────────────

    @Test
    fun `recordOurPick fills slot and advances pickIndex`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        val h = hero(7)
        manager.recordOurPick(h, slot = 0, followedRecommendation = false)
        val s = manager.session.value
        assertEquals(h, s.ourPicks[0])
        assertEquals(1, s.currentPickIndex)
    }

    @Test
    fun `recordOurPick with followedRecommendation=true increments both counters`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        manager.recordOurPick(hero(1), slot = 0, followedRecommendation = true)
        val s = manager.session.value
        assertEquals(1, s.followedRecommendations)
        assertEquals(1, s.totalRecommendations)
    }

    @Test
    fun `recordOurPick with followedRecommendation=false increments only totalRecommendations`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        manager.recordOurPick(hero(1), slot = 0, followedRecommendation = false)
        val s = manager.session.value
        assertEquals(0, s.followedRecommendations)
        assertEquals(1, s.totalRecommendations)
    }

    @Test
    fun `recordEnemyPick fills slot and advances pickIndex`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        val h = hero(5)
        manager.recordEnemyPick(h, slot = 0)
        val s = manager.session.value
        assertEquals(h, s.enemyPicks[0])
        assertEquals(1, s.currentPickIndex)
    }

    // ── Undo ─────────────────────────────────────────────────────────────────

    @Test
    fun `undo on empty stack is a no-op`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        val before = manager.session.value
        manager.undo()
        assertEquals(before, manager.session.value)
    }

    @Test
    fun `undo after recordOurBan clears the slot`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.recordOurBan(hero(1), round = 1, slot = 0)
        manager.undo()
        assertNull(manager.session.value.ourBansR1[0])
    }

    @Test
    fun `undo after recordOurPick clears the slot and decrements pickIndex`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        manager.recordOurPick(hero(1), slot = 0)
        manager.undo()
        val s = manager.session.value
        assertNull(s.ourPicks[0])
        assertEquals(0, s.currentPickIndex)
    }

    // ── Computed properties ───────────────────────────────────────────────────

    @Test
    fun `unavailableIds includes both banned and picked heroes`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.recordOurBan(hero(1), round = 1, slot = 0)
        manager.recordEnemyPick(hero(2), slot = 0)
        val unavailable = manager.session.value.unavailableIds
        assertTrue(1 in unavailable)
        assertTrue(2 in unavailable)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset returns session to initial idle state`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        manager.recordOurPick(hero(3), slot = 0)
        manager.reset()
        val s = manager.session.value
        assertEquals(DraftPhase.IDLE, s.phase)
        assertTrue(s.ourPicks.all { it == null })
        assertEquals(0, s.currentPickIndex)
        assertEquals(0, s.followedRecommendations)
        assertEquals(0, s.totalRecommendations)
    }

    // ── Swap ─────────────────────────────────────────────────────────────────

    @Test
    fun `swapOurHeroes exchanges two slots`() {
        manager.initSession(Rank.EPIC, ourTeamFirst = true)
        manager.startPickPhase()
        manager.recordOurPick(hero(10), slot = 0)
        manager.recordOurPick(hero(20), slot = 1)
        manager.swapOurHeroes(fromSlot = 0, toSlot = 1)
        val s = manager.session.value
        assertEquals(20, s.ourPicks[0]?.id)
        assertEquals(10, s.ourPicks[1]?.id)
    }
}
