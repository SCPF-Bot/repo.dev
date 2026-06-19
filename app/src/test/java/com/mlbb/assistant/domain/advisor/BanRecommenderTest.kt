package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.domain.scoring.ScoreWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BanRecommender.rank].
 *
 * Scoring formula:
 *   metaScore = (winRate - 0.50).coerceAtLeast(0) * 2 + banRate * 1.5
 *   bonus     = toxicMechanic(+0.30) + isOP(+0.25) + laneBonus(+0.10)
 *   total     = (metaScore + bonuses).coerceIn(0, 1)
 * Result is sorted descending and limited to top 3.
 */
class BanRecommenderTest {

    private val weights = ScoreWeights(meta = 0.5f, counter = 0.3f, synergy = 0.2f)

    private fun hero(
        id: Int,
        winRate: Double = 0.50,
        banRate: Double = 0.05,
        isToxicMechanic: Boolean = false,
        isOP: Boolean = false,
        lane: Lane = Lane.GOLD
    ) = Hero(
        id               = id,
        name             = "Hero$id",
        role             = "Fighter",
        secondaryRole    = null,
        lane             = lane,
        tier             = Tier.B,
        patchTrend       = 0.0,
        winRate          = winRate,
        pickRate         = 0.10,
        banRate          = banRate,
        imageUrl         = "",
        counters         = emptyList(),
        counteredBy      = emptyList(),
        synergies        = emptyList(),
        recommendedSpells = emptyList(),
        coreItems        = emptyList(),
        flexLanes        = emptyList(),
        isToxicMechanic  = isToxicMechanic,
        isOP             = isOP
    )

    // ── Pool filtering ───────────────────────────────────────────────────────

    @Test
    fun `empty pool returns empty list`() {
        val result = BanRecommender.rank(emptyList(), emptySet(), emptySet(), weights)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `banned hero is excluded from recommendations`() {
        val h = hero(1, winRate = 0.70, banRate = 0.40, isOP = true)
        val result = BanRecommender.rank(listOf(h), bannedIds = setOf(1), pickedIds = emptySet(), weights = weights)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `picked hero is excluded from recommendations`() {
        val h = hero(1, winRate = 0.70, isOP = true)
        val result = BanRecommender.rank(listOf(h), bannedIds = emptySet(), pickedIds = setOf(1), weights = weights)
        assertTrue(result.isEmpty())
    }

    // ── Result cap ───────────────────────────────────────────────────────────

    @Test
    fun `result is capped at 3 even with larger pool`() {
        val heroes = (1..10).map { hero(it) }
        val result = BanRecommender.rank(heroes, emptySet(), emptySet(), weights)
        assertTrue(result.size <= 3)
    }

    // ── Bonus effects ────────────────────────────────────────────────────────

    @Test
    fun `toxic mechanic hero scores higher than identical non-toxic hero`() {
        val toxic  = hero(1, winRate = 0.55, isToxicMechanic = true)
        val normal = hero(2, winRate = 0.55)
        val result = BanRecommender.rank(listOf(toxic, normal), emptySet(), emptySet(), weights)
        assertEquals(toxic.id, result.first().hero.id)
    }

    @Test
    fun `OP hero scores higher than identical non-OP hero`() {
        val op     = hero(1, winRate = 0.55, isOP = true)
        val normal = hero(2, winRate = 0.55)
        val result = BanRecommender.rank(listOf(op, normal), emptySet(), emptySet(), weights)
        assertEquals(op.id, result.first().hero.id)
    }

    @Test
    fun `high winRate hero scores higher than low winRate hero`() {
        val strong = hero(1, winRate = 0.70)
        val weak   = hero(2, winRate = 0.40)
        val result = BanRecommender.rank(listOf(strong, weak), emptySet(), emptySet(), weights)
        assertEquals(strong.id, result.first().hero.id)
    }

    // ── Score bounds ─────────────────────────────────────────────────────────

    @Test
    fun `score is always in range 0 to 1`() {
        val heroes = listOf(
            hero(1, winRate = 1.0, banRate = 1.0, isToxicMechanic = true, isOP = true),
            hero(2, winRate = 0.0, banRate = 0.0)
        )
        val result = BanRecommender.rank(heroes, emptySet(), emptySet(), weights)
        result.forEach { suggestion ->
            assertTrue("Score ${suggestion.score} out of [0,1]", suggestion.score in 0f..1f)
        }
    }

    // ── Badge labels ─────────────────────────────────────────────────────────

    @Test
    fun `toxic mechanic hero gets Toxic badge`() {
        val h = hero(1, isToxicMechanic = true)
        val result = BanRecommender.rank(listOf(h), emptySet(), emptySet(), weights)
        assertEquals("Toxic", result.first().badgeLabel)
    }

    @Test
    fun `OP hero gets OP Meta badge when not toxic`() {
        val h = hero(1, isOP = true)
        val result = BanRecommender.rank(listOf(h), emptySet(), emptySet(), weights)
        assertEquals("OP Meta", result.first().badgeLabel)
    }

    @Test
    fun `hero with high ban rate gets High Ban badge`() {
        val h = hero(1, banRate = 0.30)   // > 0.25 threshold
        val result = BanRecommender.rank(listOf(h), emptySet(), emptySet(), weights)
        assertEquals("High Ban", result.first().badgeLabel)
    }

    // ── Lane preference ──────────────────────────────────────────────────────

    @Test
    fun `preferred lane hero ranks above equal hero without lane preference`() {
        val laneHero  = hero(1, winRate = 0.55, lane = Lane.MID)
        val otherHero = hero(2, winRate = 0.55, lane = Lane.GOLD)
        val result = BanRecommender.rank(
            availableHeroes  = listOf(laneHero, otherHero),
            bannedIds        = emptySet(),
            pickedIds        = emptySet(),
            weights          = weights,
            preferredLanes   = listOf("MID")
        )
        assertEquals(laneHero.id, result.first().hero.id)
    }
}
