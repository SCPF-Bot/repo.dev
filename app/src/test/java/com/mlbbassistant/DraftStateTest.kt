package com.mlbbassistant

import com.mlbbassistant.data.model.DraftState
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroLane
import com.mlbbassistant.data.model.HeroRole
import org.junit.Assert.*
import org.junit.Test

class DraftStateTest {

    private fun hero(id: Int) = Hero(id, "Hero$id", HeroRole.FIGHTER, lane = HeroLane.EXP)

    @Test
    fun `unavailableIds includes all picks and bans`() {
        val state = DraftState(
            allyPicks  = listOf(hero(1), hero(2)),
            enemyPicks = listOf(hero(3)),
            bans       = listOf(hero(4), hero(5))
        )
        assertEquals(setOf(1, 2, 3, 4, 5), state.unavailableIds)
    }

    @Test
    fun `isComplete is false until 5 ally and 5 enemy picks`() {
        val partial = DraftState(
            allyPicks  = (1..4).map { hero(it) },
            enemyPicks = (5..9).map { hero(it) }
        )
        assertFalse(partial.isComplete)
    }

    @Test
    fun `isComplete is true with 5 ally and 5 enemy picks`() {
        val full = DraftState(
            allyPicks  = (1..5).map { hero(it) },
            enemyPicks = (6..10).map { hero(it) }
        )
        assertTrue(full.isComplete)
    }

    @Test
    fun `allPicked combines ally and enemy picks`() {
        val state = DraftState(
            allyPicks  = listOf(hero(1)),
            enemyPicks = listOf(hero(2))
        )
        assertEquals(listOf(hero(1), hero(2)), state.allPicked)
    }

    @Test
    fun `empty state has empty unavailableIds`() {
        assertTrue(DraftState().unavailableIds.isEmpty())
    }
}
