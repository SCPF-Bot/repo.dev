package com.mlbbassistant

import com.mlbbassistant.core.DraftEngine
import com.mlbbassistant.data.model.DraftState
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroLane
import com.mlbbassistant.data.model.HeroRole
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DraftEngineTest {

    private lateinit var engine: DraftEngine

    // Minimal hero fixtures
    private val heroA = Hero(1, "HeroA", HeroRole.ASSASSIN, lane = HeroLane.JUNGLE,
        winRate = 0.55f, counters = listOf(2), synergies = listOf(3))
    private val heroB = Hero(2, "HeroB", HeroRole.TANK, lane = HeroLane.ROAM,
        winRate = 0.50f)
    private val heroC = Hero(3, "HeroC", HeroRole.SUPPORT, lane = HeroLane.ROAM,
        winRate = 0.52f, synergies = listOf(1))
    private val heroD = Hero(4, "HeroD", HeroRole.MAGE, lane = HeroLane.MID,
        winRate = 0.48f)

    private val pool = listOf(heroA, heroB, heroC, heroD)

    @Before
    fun setUp() {
        engine = DraftEngine()
    }

    @Test
    fun `suggest returns at most topN results`() {
        val results = engine.suggest(pool, DraftState(), topN = 2)
        assertEquals(2, results.size)
    }

    @Test
    fun `suggest returns empty list for empty pool`() {
        val results = engine.suggest(emptyList(), DraftState())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `suggest scores are in descending order`() {
        val results = engine.suggest(pool, DraftState())
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].score >= results[i + 1].score)
        }
    }

    @Test
    fun `suggest all scores are in valid range`() {
        val results = engine.suggest(pool, DraftState())
        results.forEach { suggestion ->
            assertTrue("score ${suggestion.score} out of range",
                suggestion.score in 0f..1f)
        }
    }

    @Test
    fun `counter score increases when enemy picks are countered`() {
        val stateWithEnemy = DraftState(enemyPicks = listOf(heroB)) // heroA counters heroB
        val stateEmpty     = DraftState()

        val withCounterList = engine.suggest(listOf(heroA), stateWithEnemy)
        val withoutCounterList = engine.suggest(listOf(heroA), stateEmpty)

        assertFalse(withCounterList.isEmpty())
        assertFalse(withoutCounterList.isEmpty())
        assertTrue(withCounterList[0].counterScore > withoutCounterList[0].counterScore)
    }

    @Test
    fun `synergy score increases when ally picks are synergistic`() {
        val stateWithAlly = DraftState(allyPicks = listOf(heroC)) // heroA has heroC in synergies
        val stateEmpty    = DraftState()

        val withSynergyList    = engine.suggest(listOf(heroA), stateWithAlly)
        val withoutSynergyList = engine.suggest(listOf(heroA), stateEmpty)

        assertFalse(withSynergyList.isEmpty())
        assertFalse(withoutSynergyList.isEmpty())
        assertTrue(withSynergyList[0].synergyScore > withoutSynergyList[0].synergyScore)
    }

    @Test
    fun `custom weights are applied`() {
        val counterHeavy = DraftEngine.Weights(meta = 0.1f, counter = 0.8f, synergy = 0.1f)
        val metaHeavy    = DraftEngine.Weights(meta = 0.8f, counter = 0.1f, synergy = 0.1f)

        val state = DraftState(enemyPicks = listOf(heroB))

        val counterResults = engine.suggest(listOf(heroA, heroD), state, weights = counterHeavy)
        val metaResults    = engine.suggest(listOf(heroA, heroD), state, weights = metaHeavy)

        // heroA counters heroB — with counter-heavy weights heroA should rank first
        assertEquals(heroA.id, counterResults.first().hero.id)
        // heroA also has higher win rate — meta-heavy still puts it first in this fixture
        assertFalse(counterResults.isEmpty())
        assertFalse(metaResults.isEmpty())
    }

    @Test
    fun `reason is never blank`() {
        val results = engine.suggest(pool, DraftState())
        results.forEach { suggestion ->
            assertTrue("reason should not be blank for ${suggestion.hero.name}",
                suggestion.reason.isNotBlank())
        }
    }
}
