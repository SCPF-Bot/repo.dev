package com.mlbb.assistant.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 3.5 — Unit tests for [PickSequenceEngine].
 *
 * Validates the MLBB 1-2-2-2-2-1 pick sequence for both first-pick
 * configurations, and the helper accessors.
 */
class PickSequenceEngineTest {

    // ── buildSequence — OUR_TEAM first ────────────────────────────────────────

    @Test
    fun sequenceHasTenEntries() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        assertEquals(10, seq.size)
    }

    @Test
    fun ourTeamFirstPickIsIndex0() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        assertEquals(TeamSide.OUR_TEAM, seq[0].side)
    }

    @Test
    fun ourTeamFirstPickIndices() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        // OUR_TEAM picks at: 0, 3, 4, 7, 8
        listOf(0, 3, 4, 7, 8).forEach { i ->
            assertEquals("Expected OUR_TEAM at $i", TeamSide.OUR_TEAM, seq[i].side)
        }
    }

    @Test
    fun enemyTeamPickIndicesWhenWePickFirst() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        // ENEMY picks at: 1, 2, 5, 6, 9
        listOf(1, 2, 5, 6, 9).forEach { i ->
            assertEquals("Expected ENEMY at $i", TeamSide.ENEMY_TEAM, seq[i].side)
        }
    }

    // ── buildSequence — ENEMY_TEAM first ──────────────────────────────────────

    @Test
    fun enemyTeamFirstPickIsIndex0() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.ENEMY_TEAM)
        assertEquals(TeamSide.ENEMY_TEAM, seq[0].side)
    }

    @Test
    fun ourTeamPickIndicesWhenEnemyPicksFirst() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.ENEMY_TEAM)
        // OUR_TEAM picks at: 1, 2, 5, 6, 9
        listOf(1, 2, 5, 6, 9).forEach { i ->
            assertEquals("Expected OUR_TEAM at $i", TeamSide.OUR_TEAM, seq[i].side)
        }
    }

    // ── pick number / index ───────────────────────────────────────────────────

    @Test
    fun pickNumberIsOneBased() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        seq.forEachIndexed { i, turn ->
            assertEquals(i + 1, turn.pickNumber)
        }
    }

    @Test
    fun indexesMatchPosition() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        seq.forEachIndexed { i, turn ->
            assertEquals(i, turn.index)
        }
    }

    // ── first / last pick flags ───────────────────────────────────────────────

    @Test
    fun onlyIndex0IsFirstPick() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        assertTrue(seq[0].isFirstPick)
        seq.drop(1).forEach { assertFalse(it.isFirstPick) }
    }

    @Test
    fun onlyIndex9IsLastPick() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        assertTrue(seq[9].isLastPick)
        seq.dropLast(1).forEach { assertFalse(it.isLastPick) }
    }

    // ── double pick flag ─────────────────────────────────────────────────────

    @Test
    fun doublePickPairsAreCorrectWhenWePickFirst() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        // Double-pick pairs: (1,2)=ENEMY, (3,4)=OUR, (5,6)=ENEMY, (7,8)=OUR
        listOf(1, 2, 3, 4, 5, 6, 7, 8).forEach { i ->
            assertTrue("Expected isDoublePick at $i", seq[i].isDoublePick)
        }
    }

    @Test
    fun singlePicksNotMarkedAsDouble() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        // Picks 0 and 9 are singleton
        assertFalse(seq[0].isDoublePick)
        assertFalse(seq[9].isDoublePick)
    }

    // ── getCurrentTurn ────────────────────────────────────────────────────────

    @Test
    fun getCurrentTurnReturnsCorrectEntry() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        for (i in seq.indices) {
            assertEquals(seq[i], PickSequenceEngine.getCurrentTurn(seq, i))
        }
    }

    @Test
    fun getCurrentTurnReturnsNullForOutOfBounds() {
        val seq = PickSequenceEngine.buildSequence(TeamSide.OUR_TEAM)
        assertNull(PickSequenceEngine.getCurrentTurn(seq, -1))
        assertNull(PickSequenceEngine.getCurrentTurn(seq, 10))
    }

    // ── symmetry ─────────────────────────────────────────────────────────────

    @Test
    fun eachTeamGetsFivePicks() {
        listOf(TeamSide.OUR_TEAM, TeamSide.ENEMY_TEAM).forEach { first ->
            val seq = PickSequenceEngine.buildSequence(first)
            assertEquals(5, seq.count { it.side == TeamSide.OUR_TEAM })
            assertEquals(5, seq.count { it.side == TeamSide.ENEMY_TEAM })
        }
    }
}
