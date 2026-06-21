package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 3.5 — DraftSession state transition and invariant tests.
 *
 * These tests verify that [DraftSessionManager] transitions states
 * correctly and that [DraftSession] computed properties hold invariants
 * after mutations.  They do NOT test file serialisation (which requires
 * Android instrumentation) but do cover the state round-trip helpers.
 */
class DraftSessionSerializationTest {

    private fun makeHero(id: Int, name: String = "Hero$id") = Hero(
        id                = id,
        name              = name,
        role              = "Marksman",
        secondaryRole     = null,
        lane              = Lane.GOLD,
        tier              = Tier.S,
        winRate           = 0.52,
        banRate           = 0.10,
        pickRate          = 0.15,
        patchTrend        = 0.0,
        imageUrl          = "",
        counters          = emptyList(),
        counteredBy       = emptyList(),
        synergies         = emptyList(),
        recommendedSpells = emptyList(),
        coreItems         = emptyList(),
        flexLanes         = emptyList(),
        isToxicMechanic   = false,
        isOP              = false
    )

    // ── initSession ───────────────────────────────────────────────────────────

    @Test
    fun initSessionSetsRankAndBanStructure() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.MYTHIC, ourTeamFirst = true)
        val s = mgr.session.value
        assertEquals(Rank.MYTHIC, s.rank)
        assertEquals(10, s.banStructure.totalBans)
    }

    @Test
    fun initSessionPhaseIsSetup() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = false)
        assertEquals(DraftPhase.SETUP, mgr.session.value.phase)
    }

    @Test
    fun initSessionRespectsOurTeamFirst() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        assertTrue(mgr.session.value.ourTeamFirst)
        mgr.initSession(Rank.EPIC, ourTeamFirst = false)
        assertFalse(mgr.session.value.ourTeamFirst)
    }

    @Test
    fun initSessionClearsPicksAndBans() {
        val mgr  = DraftSessionManager()
        val hero = makeHero(1)
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.recordOurBan(hero, round = 1, slot = 0)

        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        assertTrue(mgr.session.value.allBannedHeroes.isEmpty())
    }

    // ── ban phase transitions ─────────────────────────────────────────────────

    @Test
    fun startBanPhaseSetsPhaseToRound1() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        assertEquals(DraftPhase.BAN_ROUND_1, mgr.session.value.phase)
    }

    @Test
    fun recordOurBanPopulatesSlot() {
        val mgr  = DraftSessionManager()
        val hero = makeHero(42)
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.recordOurBan(hero, round = 1, slot = 0)
        assertEquals(hero, mgr.session.value.ourBansR1[0])
    }

    @Test
    fun allBannedHeroesIncludesBothSides() {
        val mgr    = DraftSessionManager()
        val ourH   = makeHero(1)
        val theirH = makeHero(2)
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.recordOurBan(ourH,   round = 1, slot = 0)
        mgr.recordEnemyBan(theirH, round = 1, slot = 0)
        val banned = mgr.session.value.allBannedHeroes
        assertTrue(banned.any { it.id == 1 })
        assertTrue(banned.any { it.id == 2 })
    }

    // ── pick phase transitions ────────────────────────────────────────────────

    @Test
    fun startPickPhaseTransitionsFromBanRound1() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.startPickPhase()
        assertEquals(DraftPhase.PICK, mgr.session.value.phase)
    }

    @Test
    fun recordOurPickPopulatesSlot() {
        val mgr  = DraftSessionManager()
        val hero = makeHero(7)
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.startPickPhase()
        mgr.recordOurPick(hero, slot = 0, followedRecommendation = true)
        assertEquals(hero, mgr.session.value.ourPicks[0])
    }

    @Test
    fun recordedPickIncreasesFollowedRecommendationsCount() {
        val mgr  = DraftSessionManager()
        val hero = makeHero(5)
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.startPickPhase()
        mgr.recordOurPick(hero, slot = 0, followedRecommendation = true)
        assertEquals(1, mgr.session.value.followedRecommendations)
    }

    // ── outcome ───────────────────────────────────────────────────────────────

    @Test
    fun setOutcomeUpdatesSessionOutcome() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.setOutcome(DraftOutcome.WIN)
        assertEquals(DraftOutcome.WIN, mgr.session.value.outcome)
    }

    @Test
    fun setOutcomeLossIsDistinctFromWin() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.setOutcome(DraftOutcome.LOSS)
        assertEquals(DraftOutcome.LOSS, mgr.session.value.outcome)
        assertFalse(DraftOutcome.WIN == mgr.session.value.outcome)
    }

    @Test
    fun setOutcomeDrawIsSupported() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.setOutcome(DraftOutcome.DRAW)
        assertEquals(DraftOutcome.DRAW, mgr.session.value.outcome)
    }

    @Test
    fun defaultOutcomeIsUnknown() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        assertEquals(DraftOutcome.UNKNOWN, mgr.session.value.outcome)
    }

    // ── isSimulation flag ─────────────────────────────────────────────────────

    @Test
    fun simulationFlagIsPreservedThroughPhaseTransitions() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true, isSimulation = true)
        mgr.startBanPhase()
        assertTrue(mgr.session.value.isSimulation)
        mgr.startPickPhase()
        assertTrue(mgr.session.value.isSimulation)
    }

    @Test
    fun nonSimulationSessionHasFalseFlag() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true, isSimulation = false)
        assertFalse(mgr.session.value.isSimulation)
    }

    @Test
    fun setSimulationToggleWorks() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.setSimulation(true)
        assertTrue(mgr.session.value.isSimulation)
        mgr.setSimulation(false)
        assertFalse(mgr.session.value.isSimulation)
    }

    // ── unavailableIds invariant ───────────────────────────────────────────────

    @Test
    fun unavailableIdsContainsBansAndPicks() {
        val mgr   = DraftSessionManager()
        val ban1  = makeHero(10)
        val pick1 = makeHero(20)
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.recordOurBan(ban1, round = 1, slot = 0)
        mgr.startPickPhase()
        mgr.recordOurPick(pick1, slot = 0, followedRecommendation = false)
        val unavailable = mgr.session.value.unavailableIds
        assertTrue(unavailable.contains(10))
        assertTrue(unavailable.contains(20))
    }

    // ── complete draft ────────────────────────────────────────────────────────

    @Test
    fun completeDraftSetsPhaseComplete() {
        val mgr = DraftSessionManager()
        mgr.initSession(Rank.EPIC, ourTeamFirst = true)
        mgr.startBanPhase()
        mgr.startPickPhase()
        mgr.completeDraft()
        assertEquals(DraftPhase.COMPLETE, mgr.session.value.phase)
    }
}
