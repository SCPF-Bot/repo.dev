package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftScoreCalculatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeHero(
        id: Int,
        name: String = "Hero$id",
        role: String = "Fighter",
        tier: Tier = Tier.A,
        counters: List<Int> = emptyList(),
        synergies: List<Int> = emptyList()
    ) = Hero(
        id                = id,
        name              = name,
        role              = role,
        secondaryRole     = null,
        lane              = Lane.MID,
        tier              = tier,
        patchTrend        = 0.0,
        winRate           = 0.50,
        pickRate          = 0.10,
        banRate           = 0.10,
        imageUrl          = "",
        counters          = counters,
        counteredBy       = emptyList(),
        synergies         = synergies,
        recommendedSpells = emptyList(),
        coreItems         = emptyList(),
        flexLanes         = emptyList(),
        isToxicMechanic   = false,
        isOP              = false
    )

    // ── Overall score bounds ──────────────────────────────────────────────────

    @Test
    fun `overall score is always clamped between 0 and 100`() {
        val ours   = listOf(makeHero(1, tier = Tier.UNKNOWN))
        val theirs = listOf(makeHero(2, tier = Tier.S_PLUS))
        val score  = DraftScoreCalculator.calculate(ours, theirs, followedRecs = 0, totalRecs = 5)
        assertTrue(score.overall in 0..100)
    }

    @Test
    fun `empty picks do not throw and produce a bounded score`() {
        val score = DraftScoreCalculator.calculate(emptyList(), emptyList(), followedRecs = 0, totalRecs = 0)
        assertTrue(score.overall in 0..100)
        assertEquals(0, score.metaAdherence)
    }

    // ── Meta adherence — regression guard for the Tier.UNKNOWN bug ────────────

    @Test
    fun `Tier UNKNOWN contributes zero, not a negative meta score`() {
        val ours  = listOf(makeHero(1, tier = Tier.UNKNOWN))
        val score = DraftScoreCalculator.calculate(ours, emptyList(), followedRecs = 0, totalRecs = 0)
        assertEquals(0, score.metaAdherence)
    }

    @Test
    fun `top tier S_PLUS picks yield the highest meta adherence`() {
        val ours  = listOf(makeHero(1, tier = Tier.S_PLUS))
        val score = DraftScoreCalculator.calculate(ours, emptyList(), followedRecs = 0, totalRecs = 0)
        assertEquals(100, score.metaAdherence)
    }

    @Test
    fun `mixed tiers average out meta adherence between best and worst`() {
        val ours    = listOf(makeHero(1, tier = Tier.S_PLUS), makeHero(2, tier = Tier.UNKNOWN))
        val soloTop = DraftScoreCalculator.calculate(listOf(makeHero(1, tier = Tier.S_PLUS)), emptyList(), 0, 0)
        val score   = DraftScoreCalculator.calculate(ours, emptyList(), followedRecs = 0, totalRecs = 0)
        assertTrue(score.metaAdherence < soloTop.metaAdherence)
        assertTrue(score.metaAdherence > 0)
    }

    // ── Counter efficiency ─────────────────────────────────────────────────────

    @Test
    fun `counter efficiency is zero when no ally counters any enemy`() {
        val ours   = listOf(makeHero(1))
        val theirs = listOf(makeHero(2))
        val score  = DraftScoreCalculator.calculate(ours, theirs, followedRecs = 0, totalRecs = 0)
        assertEquals(0, score.counterEfficiency)
    }

    @Test
    fun `counter efficiency is 100 when every ally counters every enemy`() {
        val ours   = listOf(makeHero(1, counters = listOf(2)))
        val theirs = listOf(makeHero(2))
        val score  = DraftScoreCalculator.calculate(ours, theirs, followedRecs = 0, totalRecs = 0)
        assertEquals(100, score.counterEfficiency)
    }

    // ── Synergy strength ───────────────────────────────────────────────────────

    @Test
    fun `synergy strength is zero with fewer than two picks`() {
        val ours  = listOf(makeHero(1))
        val score = DraftScoreCalculator.calculate(ours, emptyList(), followedRecs = 0, totalRecs = 0)
        assertEquals(0, score.synergyStrength)
    }

    @Test
    fun `synergy strength is 100 when the only pair synergises`() {
        val ours = listOf(makeHero(1, synergies = listOf(2)), makeHero(2))
        val score = DraftScoreCalculator.calculate(ours, emptyList(), followedRecs = 0, totalRecs = 0)
        assertEquals(100, score.synergyStrength)
    }

    // ── Recommendation follow rate influence ──────────────────────────────────

    @Test
    fun `following all recommendations scores at least as high as following none, all else equal`() {
        val ours    = listOf(makeHero(1, tier = Tier.A))
        val theirs  = listOf(makeHero(2, tier = Tier.A))
        val followed = DraftScoreCalculator.calculate(ours, theirs, followedRecs = 5, totalRecs = 5)
        val ignored  = DraftScoreCalculator.calculate(ours, theirs, followedRecs = 0, totalRecs = 5)
        assertTrue(followed.overall >= ignored.overall)
    }

    // ── Enemy threats ──────────────────────────────────────────────────────────

    @Test
    fun `no outstanding threats message appears when enemy comp is unremarkable`() {
        val theirs = listOf(makeHero(1, role = "Tank"), makeHero(2, role = "Support"))
        val score  = DraftScoreCalculator.calculate(emptyList(), theirs, followedRecs = 0, totalRecs = 0)
        assertTrue(score.enemyThreats.any { it.contains("No outstanding threats") })
    }

    @Test
    fun `heavy physical enemy comp surfaces a physical burst threat`() {
        val theirs = listOf(
            makeHero(1, role = "Marksman"),
            makeHero(2, role = "Assassin"),
            makeHero(3, role = "Fighter")
        )
        val score = DraftScoreCalculator.calculate(emptyList(), theirs, followedRecs = 0, totalRecs = 0)
        assertTrue(score.enemyThreats.any { it.contains("physical burst") })
    }
}
