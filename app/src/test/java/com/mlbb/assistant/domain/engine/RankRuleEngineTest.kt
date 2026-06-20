package com.mlbb.assistant.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 3.5 — Unit tests for [RankRuleEngine].
 *
 * Validates ban-structure rules per rank tier and the string / ban-count
 * inference helpers.
 */
class RankRuleEngineTest {

    // ── getBanStructure ───────────────────────────────────────────────────────

    @Test
    fun epicBanStructureHas6TotalAndNoRound2() {
        val s = RankRuleEngine.getBanStructure(Rank.EPIC)
        assertEquals(6, s.totalBans)
        assertEquals(3, s.round1PerTeam)
        assertEquals(0, s.round2PerTeam)
        assertFalse(s.hasRound2)
    }

    @Test
    fun legendBanStructureHas8TotalAndRound2() {
        val s = RankRuleEngine.getBanStructure(Rank.LEGEND)
        assertEquals(8, s.totalBans)
        assertEquals(3, s.round1PerTeam)
        assertEquals(1, s.round2PerTeam)
        assertTrue(s.hasRound2)
    }

    @Test
    fun mythicBanStructureHas10TotalAndRound2() {
        val s = RankRuleEngine.getBanStructure(Rank.MYTHIC)
        assertEquals(10, s.totalBans)
        assertEquals(3, s.round1PerTeam)
        assertEquals(2, s.round2PerTeam)
        assertTrue(s.hasRound2)
    }

    @Test
    fun mythicalHonorBanStructureMatchesMythic() {
        val mythic = RankRuleEngine.getBanStructure(Rank.MYTHIC)
        val honor  = RankRuleEngine.getBanStructure(Rank.MYTHICAL_HONOR)
        assertEquals(mythic, honor)
    }

    @Test
    fun mythicalGloryBanStructureMatchesMythic() {
        val mythic = RankRuleEngine.getBanStructure(Rank.MYTHIC)
        val glory  = RankRuleEngine.getBanStructure(Rank.MYTHICAL_GLORY)
        assertEquals(mythic, glory)
    }

    @Test
    fun immortalBanStructureMatchesMythic() {
        val mythic   = RankRuleEngine.getBanStructure(Rank.MYTHIC)
        val immortal = RankRuleEngine.getBanStructure(Rank.IMMORTAL)
        assertEquals(mythic, immortal)
    }

    @Test
    fun unknownBanStructureFallsBackToEpic() {
        val s = RankRuleEngine.getBanStructure(Rank.UNKNOWN)
        assertEquals(6, s.totalBans)
        assertFalse(s.hasRound2)
    }

    // ── round totals ─────────────────────────────────────────────────────────

    @Test
    fun round1TotalIsDoubleRound1PerTeam() {
        Rank.entries.forEach { rank ->
            val s = RankRuleEngine.getBanStructure(rank)
            assertEquals(s.round1PerTeam * 2, s.round1Total)
        }
    }

    @Test
    fun round2TotalIsDoubleRound2PerTeam() {
        Rank.entries.forEach { rank ->
            val s = RankRuleEngine.getBanStructure(rank)
            assertEquals(s.round2PerTeam * 2, s.round2Total)
        }
    }

    // ── fromString ────────────────────────────────────────────────────────────

    @Test
    fun fromStringRecognisesImmortal() {
        assertEquals(Rank.IMMORTAL, RankRuleEngine.fromString("Immortal"))
        assertEquals(Rank.IMMORTAL, RankRuleEngine.fromString("IMMORTAL I"))
    }

    @Test
    fun fromStringRecognisesMythicalGlory() {
        assertEquals(Rank.MYTHICAL_GLORY, RankRuleEngine.fromString("Mythical Glory"))
    }

    @Test
    fun fromStringRecognisesMythicalHonor() {
        assertEquals(Rank.MYTHICAL_HONOR, RankRuleEngine.fromString("Mythical Honor"))
    }

    @Test
    fun fromStringRecognisesMythicWithoutSuffix() {
        assertEquals(Rank.MYTHIC, RankRuleEngine.fromString("Mythic"))
    }

    @Test
    fun fromStringRecognisesLegend() {
        assertEquals(Rank.LEGEND, RankRuleEngine.fromString("legend iii"))
    }

    @Test
    fun fromStringRecognisesEpic() {
        assertEquals(Rank.EPIC, RankRuleEngine.fromString("Epic IV"))
    }

    @Test
    fun fromStringReturnsUnknownForGibberish() {
        assertEquals(Rank.UNKNOWN, RankRuleEngine.fromString(""))
        assertEquals(Rank.UNKNOWN, RankRuleEngine.fromString("Warrior"))
    }

    // ── inferFromBanCount ─────────────────────────────────────────────────────

    @Test
    fun inferFrom10BansReturnsMythic() {
        assertEquals(Rank.MYTHIC, RankRuleEngine.inferFromBanCount(10))
        assertEquals(Rank.MYTHIC, RankRuleEngine.inferFromBanCount(12))
    }

    @Test
    fun inferFrom8BansReturnsLegend() {
        assertEquals(Rank.LEGEND, RankRuleEngine.inferFromBanCount(8))
        assertEquals(Rank.LEGEND, RankRuleEngine.inferFromBanCount(9))
    }

    @Test
    fun inferFrom6BansReturnsEpic() {
        assertEquals(Rank.EPIC, RankRuleEngine.inferFromBanCount(6))
        assertEquals(Rank.EPIC, RankRuleEngine.inferFromBanCount(0))
    }

    // ── getBannerSlots ────────────────────────────────────────────────────────

    @Test
    fun epicHas3BannerSlots() {
        assertEquals(3, RankRuleEngine.getBannerSlots(Rank.EPIC).size)
    }

    @Test
    fun nonEpicHas5BannerSlots() {
        listOf(Rank.LEGEND, Rank.MYTHIC, Rank.MYTHICAL_HONOR, Rank.MYTHICAL_GLORY, Rank.IMMORTAL)
            .forEach { rank ->
                assertEquals(5, RankRuleEngine.getBannerSlots(rank).size)
            }
    }
}
