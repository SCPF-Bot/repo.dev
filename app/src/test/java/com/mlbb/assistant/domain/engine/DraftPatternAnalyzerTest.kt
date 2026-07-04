package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.model.DraftOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftPatternAnalyzerTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeItem(
        outcome: DraftOutcome,
        timestamp: Long = 0L,
        draftScore: Int = 60,
        followed: Int = 3,
        total: Int = 5
    ) = DraftHistoryItem(
        id                      = 0,
        timestamp               = timestamp,
        rank                    = "Epic",
        draftScore              = draftScore,
        metaScore               = draftScore,
        counterScore            = draftScore,
        synergyScore            = draftScore,
        followedRecommendations = followed,
        totalRecommendations    = total,
        outcome                 = outcome
    )

    // ── Empty / degenerate input ─────────────────────────────────────────────

    @Test
    fun `returns null for empty history`() {
        assertNull(DraftPatternAnalyzer.analyze(emptyList()))
    }

    @Test
    fun `totalSessions counts all entries including unknown outcomes`() {
        val history = listOf(
            makeItem(DraftOutcome.WIN),
            makeItem(DraftOutcome.UNKNOWN)
        )
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals(2, insights!!.totalSessions)
    }

    @Test
    fun `win rate ignores unknown-outcome sessions`() {
        val history = listOf(
            makeItem(DraftOutcome.WIN),
            makeItem(DraftOutcome.LOSS),
            makeItem(DraftOutcome.UNKNOWN),
            makeItem(DraftOutcome.UNKNOWN)
        )
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals(0.5f, insights!!.winRate, 0.001f)
    }

    @Test
    fun `win rate is zero when there are no labelled outcomes`() {
        val history = listOf(makeItem(DraftOutcome.UNKNOWN), makeItem(DraftOutcome.UNKNOWN))
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals(0f, insights!!.winRate, 0.001f)
    }

    // ── Follow-rate & score aggregates ───────────────────────────────────────

    @Test
    fun `follow rate is averaged separately for wins and losses`() {
        val history = listOf(
            makeItem(DraftOutcome.WIN,  followed = 4, total = 5),  // 0.8
            makeItem(DraftOutcome.WIN,  followed = 2, total = 5),  // 0.4
            makeItem(DraftOutcome.LOSS, followed = 1, total = 5)   // 0.2
        )
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals(0.6f, insights!!.followRateWins, 0.001f)
        assertEquals(0.2f, insights.followRateLosses, 0.001f)
    }

    @Test
    fun `follow rate treats zero total recommendations as zero, not divide-by-zero`() {
        val history = listOf(makeItem(DraftOutcome.WIN, followed = 0, total = 0))
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals(0f, insights!!.followRateWins, 0.001f)
    }

    @Test
    fun `avg scores are computed independently for wins and losses`() {
        val history = listOf(
            makeItem(DraftOutcome.WIN,  draftScore = 80),
            makeItem(DraftOutcome.WIN,  draftScore = 60),
            makeItem(DraftOutcome.LOSS, draftScore = 30)
        )
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals(70f, insights!!.avgScoreWins, 0.001f)
        assertEquals(30f, insights.avgScoreLosses, 0.001f)
    }

    // ── Streak computation ────────────────────────────────────────────────────

    @Test
    fun `streak reports the most recent consecutive outcome by timestamp`() {
        val history = listOf(
            makeItem(DraftOutcome.LOSS, timestamp = 1),
            makeItem(DraftOutcome.WIN,  timestamp = 2),
            makeItem(DraftOutcome.WIN,  timestamp = 3),
            makeItem(DraftOutcome.WIN,  timestamp = 4)
        )
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals("3 Win streak", insights!!.currentStreak)
    }

    @Test
    fun `streak ignores unknown-outcome sessions entirely`() {
        val history = listOf(
            makeItem(DraftOutcome.UNKNOWN, timestamp = 5),
            makeItem(DraftOutcome.WIN,     timestamp = 4)
        )
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals("1 Win streak", insights!!.currentStreak)
    }

    @Test
    fun `streak message reflects no recorded outcomes when history is all unknown`() {
        val history = listOf(makeItem(DraftOutcome.UNKNOWN), makeItem(DraftOutcome.UNKNOWN))
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertEquals("No recorded outcomes yet", insights!!.currentStreak)
    }

    // ── Top insight thresholds ────────────────────────────────────────────────

    @Test
    fun `top insight prompts for more sessions below the minimum sample size`() {
        val history = (1..3).map { makeItem(DraftOutcome.WIN) }
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertTrue(insights!!.topInsight.contains("more rated drafts"))
    }

    @Test
    fun `top insight highlights following recommendations when it strongly correlates with wins`() {
        val history = buildList {
            repeat(3) { add(makeItem(DraftOutcome.WIN,  followed = 5, total = 5)) }
            repeat(3) { add(makeItem(DraftOutcome.LOSS, followed = 0, total = 5)) }
        }
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertTrue(insights!!.topInsight.contains("Following recommendations"))
    }

    @Test
    fun `top insight highlights strong win rate at or above 60 percent`() {
        val history = buildList {
            repeat(4) { add(makeItem(DraftOutcome.WIN)) }
            repeat(1) { add(makeItem(DraftOutcome.LOSS)) }
        }
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertTrue(insights!!.topInsight.contains("Strong win rate"))
    }

    @Test
    fun `top insight flags low win rate at or below 35 percent`() {
        val history = buildList {
            repeat(1) { add(makeItem(DraftOutcome.WIN)) }
            repeat(4) { add(makeItem(DraftOutcome.LOSS)) }
        }
        val insights = DraftPatternAnalyzer.analyze(history)
        assertNotNull(insights)
        assertTrue(insights!!.topInsight.contains("Low win rate"))
    }
}
