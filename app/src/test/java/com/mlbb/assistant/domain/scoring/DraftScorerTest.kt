package com.mlbb.assistant.domain.scoring

import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure mathematical scoring formula exposed by [DraftScorer.computeScore]
 * and the full per-hero scoring pipeline exposed by [DraftScorer.score] / [DraftScorer.rankAll].
 *
 * Formula: score = metaWeight * winRate
 *                + counterWeight * (enemies countered / total enemies)
 *                + synergyWeight * (allies with synergy / total allies)
 */
class DraftScorerTest {

    private val weights = ScoreWeights(meta = 0.5f, counter = 0.3f, synergy = 0.2f)

    private fun hero(
        id: Int,
        winRate: Double = 0.5,
        counters: List<Int> = emptyList(),
        synergies: List<Int> = emptyList(),
        counteredBy: List<Int> = emptyList()
    ) = Hero(
        id                = id,
        name              = "Hero$id",
        role              = "Fighter",
        secondaryRole     = null,
        lane              = Lane.GOLD,
        tier              = Tier.B,
        patchTrend        = 0.0,
        winRate           = winRate,
        pickRate          = 0.1,
        banRate           = 0.05,
        imageUrl          = "",
        counters          = counters,
        counteredBy       = counteredBy,
        synergies         = synergies,
        recommendedSpells = emptyList(),
        coreItems         = emptyList(),
        flexLanes         = emptyList(),
        isToxicMechanic   = false,
        isOP              = false
    )

    // ----- meta score -----

    @Test
    fun `no allies or enemies returns meta score only`() {
        val score = DraftScorer.computeScore(hero(1, winRate = 0.6), emptyList(), emptyList(), weights)
        // 0.5 * 0.6 = 0.30
        assertEquals(0.30, score, 1e-9)
    }

    @Test
    fun `zero winRate with no context scores zero`() {
        val score = DraftScorer.computeScore(hero(1, winRate = 0.0), emptyList(), emptyList(), weights)
        assertEquals(0.0, score, 1e-9)
    }

    // ----- counter score -----

    @Test
    fun `counter score increases when hero counters all enemies`() {
        val enemy          = hero(2)
        val heroWithCounter = hero(1, counters = listOf(2))
        val heroNoCounter   = hero(1)

        val withCounter = DraftScorer.computeScore(heroWithCounter, emptyList(), listOf(enemy), weights)
        val noCounter   = DraftScorer.computeScore(heroNoCounter,  emptyList(), listOf(enemy), weights)

        assertTrue(withCounter > noCounter)
    }

    @Test
    fun `counter fraction is correct for partial enemy coverage`() {
        val enemies = listOf(hero(2), hero(3), hero(4))
        val h = hero(1, winRate = 0.0, counters = listOf(2, 3))  // counters 2 of 3 enemies

        val score = DraftScorer.computeScore(h, emptyList(), enemies, weights)
        // meta=0, synergy=0, counter = 0.3 * (2/3)
        assertEquals(0.3 * (2.0 / 3.0), score, 1e-9)
    }

    @Test
    fun `no enemies gives zero counter score regardless of counter list`() {
        val h = hero(1, winRate = 0.0, counters = listOf(5, 6, 7))
        val score = DraftScorer.computeScore(h, emptyList(), emptyList(), weights)
        assertEquals(0.0, score, 1e-9)
    }

    // ----- synergy score -----

    @Test
    fun `synergy score increases when hero synergises with ally`() {
        val ally        = hero(3)
        val heroWithSyn = hero(1, synergies = listOf(3))
        val heroNoSyn   = hero(1)

        val withSyn = DraftScorer.computeScore(heroWithSyn, listOf(ally), emptyList(), weights)
        val noSyn   = DraftScorer.computeScore(heroNoSyn,   listOf(ally), emptyList(), weights)

        assertTrue(withSyn > noSyn)
    }

    @Test
    fun `no allies gives zero synergy score regardless of synergy list`() {
        val h = hero(1, winRate = 0.0, synergies = listOf(9, 10))
        val score = DraftScorer.computeScore(h, emptyList(), emptyList(), weights)
        assertEquals(0.0, score, 1e-9)
    }

    // ----- combined / maximum -----

    @Test
    fun `perfect hero scores 1_0 with full winRate counter and synergy`() {
        val ally  = hero(2)
        val enemy = hero(3)
        val h = hero(1, winRate = 1.0, counters = listOf(3), synergies = listOf(2))

        val score = DraftScorer.computeScore(h, listOf(ally), listOf(enemy), weights)
        // 0.5*1.0 + 0.3*1.0 + 0.2*1.0 = 1.0
        assertEquals(1.0, score, 1e-9)
    }

    @Test
    fun `score is non-negative for any valid input`() {
        val h = hero(1, winRate = 0.0)
        val score = DraftScorer.computeScore(h, listOf(hero(2)), listOf(hero(3)), weights)
        assertTrue(score >= 0.0)
    }

    // ----- score() pipeline -----

    @Test
    fun `score function does not throw for any valid pick index`() {
        val h = hero(1, winRate = 0.5, synergies = listOf(2))
        val ally = hero(2)
        for (idx in 0..10) {
            val result = DraftScorer.score(
                candidate    = h,
                alliedPicks  = listOf(ally),
                enemyPicks   = emptyList(),
                bannedIds    = emptySet(),
                weights      = weights,
                missingLanes = emptyList(),
                currentTurn  = null,
                pickIndex    = idx,
                maxPickIndex = 10
            )
            assertTrue("Score at pickIndex=$idx must be in [0,1]", result.totalScore in 0f..1f)
        }
    }

    @Test
    fun `score produces a different total at pickIndex 0 vs pickIndex 9 for asymmetric weights`() {
        // With asymmetric base weights (synergy != counter), the adaptive curve should
        // produce different totals at early vs late picks for a hero with strong synergy.
        val asymWeights = ScoreWeights(0.50f, 0.30f, 0.20f)  // meta, synergy, counter
        val ally = hero(2)
        val h    = hero(1, winRate = 0.50, synergies = listOf(2))

        val earlyScore = DraftScorer.score(h, listOf(ally), emptyList(), emptySet(),
            asymWeights, emptyList(), null, pickIndex = 0, maxPickIndex = 10)
        val lateScore  = DraftScorer.score(h, listOf(ally), emptyList(), emptySet(),
            asymWeights, emptyList(), null, pickIndex = 9, maxPickIndex = 10)

        // Adaptive weights shift at later picks; total scores must differ.
        assertNotEquals(
            "Adaptive weights should produce different totals at pickIndex=0 vs 9",
            earlyScore.totalScore, lateScore.totalScore
        )
    }

    // ----- rankAll -----

    @Test
    fun `rankAll excludes banned heroes`() {
        val pool = listOf(hero(1), hero(2), hero(3))
        val result = DraftScorer.rankAll(
            pool        = pool,
            alliedPicks = emptyList(),
            enemyPicks  = emptyList(),
            bannedIds   = setOf(2),
            weights     = weights,
            currentTurn = null
        )
        assertTrue("Banned hero 2 must not appear in results",
            result.none { it.hero.id == 2 })
        assertEquals(2, result.size)
    }

    @Test
    fun `rankAll returns results sorted by descending totalScore`() {
        val pool = listOf(
            hero(1, winRate = 0.70),
            hero(2, winRate = 0.40),
            hero(3, winRate = 0.55)
        )
        val result = DraftScorer.rankAll(pool, emptyList(), emptyList(), emptySet(), weights, null)
        for (i in 0 until result.size - 1) {
            assertTrue("Results must be sorted descending",
                result[i].totalScore >= result[i + 1].totalScore)
        }
    }

    @Test
    fun `computeBounds returns DEFAULT for pool smaller than 4`() {
        val small = listOf(hero(1), hero(2), hero(3))
        val bounds = DraftScorer.computeBounds(small)
        assertEquals(DraftScorer.ScoreBounds.DEFAULT, bounds)
    }
}
