package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.domain.scoring.ScoreWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WeightCalibratorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeItem(
        outcome: DraftOutcome,
        metaScore: Int    = 50,
        synergyScore: Int = 50,
        counterScore: Int = 50
    ) = DraftHistoryItem(
        id                      = 0,
        timestamp               = 0L,
        rank                    = "Epic",
        draftScore              = metaScore + synergyScore + counterScore,
        metaScore               = metaScore,
        counterScore            = counterScore,
        synergyScore            = synergyScore,
        followedRecommendations = 0,
        totalRecommendations    = 5,
        outcome                 = outcome
    )

    private val defaultWeights = ScoreWeights.DEFAULT

    // ── Below MIN_SESSIONS — should return null ───────────────────────────────

    @Test
    fun `returns null when history has fewer than 10 labelled sessions`() {
        val history = (1..9).map { makeItem(DraftOutcome.WIN) }
        assertNull(WeightCalibrator.calibrate(history, defaultWeights))
    }

    @Test
    fun `returns null when all sessions are draws`() {
        val history = (1..15).map { makeItem(DraftOutcome.DRAW) }
        assertNull(WeightCalibrator.calibrate(history, defaultWeights))
    }

    @Test
    fun `returns null when only wins exist`() {
        val allWins = (1..15).map { makeItem(DraftOutcome.WIN) }
        assertNull(WeightCalibrator.calibrate(allWins, defaultWeights))
    }

    // ── Valid calibration — weights remain normalised ─────────────────────────

    @Test
    fun `suggested weights sum to 1 when calibration succeeds`() {
        val history = buildList {
            repeat(8) { add(makeItem(DraftOutcome.WIN,  metaScore = 80)) }
            repeat(7) { add(makeItem(DraftOutcome.LOSS, metaScore = 30)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        val weights = result!!.suggestedWeights
        assertEquals(1.0f, weights.meta + weights.synergy + weights.counter, 0.01f)
    }

    @Test
    fun `dominant meta signal shifts meta weight upward`() {
        val history = buildList {
            repeat(8) { add(makeItem(DraftOutcome.WIN,  metaScore = 90, synergyScore = 40, counterScore = 40)) }
            repeat(7) { add(makeItem(DraftOutcome.LOSS, metaScore = 20, synergyScore = 40, counterScore = 40)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        assertTrue(result!!.suggestedWeights.meta >= defaultWeights.meta)
    }

    @Test
    fun `dominant counter signal shifts counter weight upward`() {
        val history = buildList {
            repeat(8) { add(makeItem(DraftOutcome.WIN,  counterScore = 90, metaScore = 40, synergyScore = 40)) }
            repeat(7) { add(makeItem(DraftOutcome.LOSS, counterScore = 10, metaScore = 40, synergyScore = 40)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        assertTrue(result!!.suggestedWeights.counter >= defaultWeights.counter)
    }

    // ── Confidence increases with more sessions ───────────────────────────────

    @Test
    fun `confidence grows with more history`() {
        val small = buildList {
            repeat(6)  { add(makeItem(DraftOutcome.WIN,  metaScore = 80)) }
            repeat(5)  { add(makeItem(DraftOutcome.LOSS, metaScore = 30)) }
        }
        val large = buildList {
            repeat(30) { add(makeItem(DraftOutcome.WIN,  metaScore = 80)) }
            repeat(30) { add(makeItem(DraftOutcome.LOSS, metaScore = 30)) }
        }
        val r1 = WeightCalibrator.calibrate(small, defaultWeights)
        val r2 = WeightCalibrator.calibrate(large, defaultWeights)
        assertNotNull(r1); assertNotNull(r2)
        assertTrue(r2!!.confidence >= r1!!.confidence)
    }

    // ── Delta clamped to MAX_DELTA ────────────────────────────────────────────

    @Test
    fun `weight adjustment does not exceed MAX_DELTA per component`() {
        val history = buildList {
            repeat(20) { add(makeItem(DraftOutcome.WIN,  metaScore = 100, synergyScore = 0, counterScore = 0)) }
            repeat(20) { add(makeItem(DraftOutcome.LOSS, metaScore = 0,   synergyScore = 100, counterScore = 100)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        val w = result!!.suggestedWeights
        assertTrue("meta delta too large",    abs(w.meta    - defaultWeights.meta)    <= 0.16f)
        assertTrue("synergy delta too large", abs(w.synergy - defaultWeights.synergy) <= 0.16f)
        assertTrue("counter delta too large", abs(w.counter - defaultWeights.counter) <= 0.16f)
    }

    // ── No component weight drops below minimum ───────────────────────────────

    @Test
    fun `no component weight drops below 0_05f after calibration`() {
        val history = buildList {
            repeat(20) { add(makeItem(DraftOutcome.WIN,  metaScore = 100, synergyScore = 0,   counterScore = 0)) }
            repeat(20) { add(makeItem(DraftOutcome.LOSS, metaScore = 0,   synergyScore = 100, counterScore = 100)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        val w = result!!.suggestedWeights
        assertTrue("meta < 0.05",    w.meta    >= 0.05f)
        assertTrue("synergy < 0.05", w.synergy >= 0.05f)
        assertTrue("counter < 0.05", w.counter >= 0.05f)
    }

    // ── No-signal case — rationale is always non-blank ────────────────────────

    @Test
    fun `calibration result always contains a rationale string`() {
        val history = buildList {
            repeat(8) { add(makeItem(DraftOutcome.WIN,  metaScore = 80)) }
            repeat(7) { add(makeItem(DraftOutcome.LOSS, metaScore = 30)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        assertTrue(result!!.rationale.isNotBlank())
    }

    // ── Confidence is bounded [0, 1] ──────────────────────────────────────────

    @Test
    fun `confidence is in range 0 to 1`() {
        val history = buildList {
            repeat(10) { add(makeItem(DraftOutcome.WIN,  metaScore = 80)) }
            repeat(10) { add(makeItem(DraftOutcome.LOSS, metaScore = 30)) }
        }
        val result = WeightCalibrator.calibrate(history, defaultWeights)
        assertNotNull(result)
        val confidence = result!!.confidence
        assertTrue("confidence $confidence < 0", confidence >= 0f)
        assertTrue("confidence $confidence > 1", confidence <= 1f)
    }
}
