package com.mlbb.assistant.domain.scoring

import com.mlbb.assistant.domain.model.Hero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftScorerTest {

    private lateinit var scorer: DraftScorer
    private val weights = ScoreWeights(metaWeight = 0.5, counterWeight = 0.3, synergyWeight = 0.2)

    private fun hero(
        id: Int,
        winRate: Double = 0.5,
        counters: List<Int> = emptyList(),
        synergies: List<Int> = emptyList()
    ) = Hero(
        id = id,
        name = "Hero$id",
        role = "Fighter",
        winRate = winRate,
        pickRate = 0.1,
        banRate = 0.05,
        imageUrl = "",
        counters = counters,
        synergies = synergies
    )

    @Before
    fun setUp() {
        scorer = DraftScorer()
    }

    // ----- meta score -----

    @Test
    fun `no allies or enemies returns meta score only`() {
        val score = scorer.computeScore(hero(1, winRate = 0.6), emptyList(), emptyList(), weights)
        // 0.5 * 0.6 = 0.30
        assertEquals(0.30, score, 1e-9)
    }

    @Test
    fun `zero winRate with no context scores zero`() {
        val score = scorer.computeScore(hero(1, winRate = 0.0), emptyList(), emptyList(), weights)
        assertEquals(0.0, score, 1e-9)
    }

    // ----- counter score -----

    @Test
    fun `counter score increases when hero counters all enemies`() {
        val enemy = hero(2)
        val heroWithCounter = hero(1, counters = listOf(2))
        val heroNoCounter  = hero(1)

        val withCounter = scorer.computeScore(heroWithCounter, emptyList(), listOf(enemy), weights)
        val noCounter   = scorer.computeScore(heroNoCounter,  emptyList(), listOf(enemy), weights)

        assertTrue(withCounter > noCounter)
    }

    @Test
    fun `counter fraction is correct for partial enemy coverage`() {
        val enemies = listOf(hero(2), hero(3), hero(4))
        val h = hero(1, winRate = 0.0, counters = listOf(2, 3))  // counters 2 of 3 enemies

        val score = scorer.computeScore(h, emptyList(), enemies, weights)
        // meta=0, synergy=0, counter = 0.3 * (2/3)
        assertEquals(0.3 * (2.0 / 3.0), score, 1e-9)
    }

    @Test
    fun `no enemies gives zero counter score regardless of counter list`() {
        val h = hero(1, winRate = 0.0, counters = listOf(5, 6, 7))
        val score = scorer.computeScore(h, emptyList(), emptyList(), weights)
        assertEquals(0.0, score, 1e-9)
    }

    // ----- synergy score -----

    @Test
    fun `synergy score increases when hero synergises with ally`() {
        val ally = hero(3)
        val heroWithSyn = hero(1, synergies = listOf(3))
        val heroNoSyn   = hero(1)

        val withSyn = scorer.computeScore(heroWithSyn, listOf(ally), emptyList(), weights)
        val noSyn   = scorer.computeScore(heroNoSyn,   listOf(ally), emptyList(), weights)

        assertTrue(withSyn > noSyn)
    }

    @Test
    fun `no allies gives zero synergy score regardless of synergy list`() {
        val h = hero(1, winRate = 0.0, synergies = listOf(9, 10))
        val score = scorer.computeScore(h, emptyList(), emptyList(), weights)
        assertEquals(0.0, score, 1e-9)
    }

    // ----- combined / maximum -----

    @Test
    fun `perfect hero scores 1_0 with full winRate counter and synergy`() {
        val ally  = hero(2)
        val enemy = hero(3)
        val h = hero(1, winRate = 1.0, counters = listOf(3), synergies = listOf(2))

        val score = scorer.computeScore(h, listOf(ally), listOf(enemy), weights)
        // 0.5*1.0 + 0.3*1.0 + 0.2*1.0 = 1.0
        assertEquals(1.0, score, 1e-9)
    }

    @Test
    fun `score is non-negative for any valid input`() {
        val h = hero(1, winRate = 0.0)
        val score = scorer.computeScore(h, listOf(hero(2)), listOf(hero(3)), weights)
        assertTrue(score >= 0.0)
    }
}
