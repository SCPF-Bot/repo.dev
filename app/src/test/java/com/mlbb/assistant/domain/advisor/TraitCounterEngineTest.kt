package com.mlbb.assistant.domain.advisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitCounterEngineTest {

    // ── Zero-input guards ─────────────────────────────────────────────────────

    @Test
    fun `returns 0 when candidate traits are empty`() {
        val enemyTraits = listOf(setOf("high_sustain", "crowd_control"))
        assertEquals(0f, TraitCounterEngine.computeBonus(emptySet(), enemyTraits))
    }

    @Test
    fun `returns 0 when enemy trait list is empty`() {
        val candidateTraits = setOf("anti_heal", "true_damage")
        assertEquals(0f, TraitCounterEngine.computeBonus(candidateTraits, emptyList()))
    }

    @Test
    fun `flat overload returns 0 with empty inputs`() {
        assertEquals(0f, TraitCounterEngine.computeBonusFlat(emptySet(), emptySet()))
        assertEquals(0f, TraitCounterEngine.computeBonusFlat(setOf("anti_heal"), emptySet()))
        assertEquals(0f, TraitCounterEngine.computeBonusFlat(emptySet(), setOf("high_sustain")))
    }

    // ── Single match ──────────────────────────────────────────────────────────

    @Test
    fun `anti_heal counters high_sustain enemy`() {
        val candidate = setOf("anti_heal")
        val enemy     = listOf(setOf("high_sustain"))
        val bonus     = TraitCounterEngine.computeBonus(candidate, enemy)
        assertEquals(TraitCounterEngine.TRAIT_BONUS_PER_MATCH, bonus, 0.001f)
    }

    @Test
    fun `armor_shred counters high_armor enemy`() {
        val candidate = setOf("armor_shred")
        val enemy     = listOf(setOf("high_armor"))
        val bonus     = TraitCounterEngine.computeBonus(candidate, enemy)
        assertEquals(TraitCounterEngine.TRAIT_BONUS_PER_MATCH, bonus, 0.001f)
    }

    @Test
    fun `crowd_control counters high_mobility enemy`() {
        val candidate = setOf("crowd_control")
        val enemy     = listOf(setOf("high_mobility"))
        val bonus     = TraitCounterEngine.computeBonus(candidate, enemy)
        assertEquals(TraitCounterEngine.TRAIT_BONUS_PER_MATCH, bonus, 0.001f)
    }

    @Test
    fun `disengage counters hard_engage enemy`() {
        val candidate = setOf("disengage")
        val enemy     = listOf(setOf("hard_engage"))
        val bonus     = TraitCounterEngine.computeBonus(candidate, enemy)
        assertEquals(TraitCounterEngine.TRAIT_BONUS_PER_MATCH, bonus, 0.001f)
    }

    @Test
    fun `shield_breaker counters heavy_shields enemy`() {
        val candidate = setOf("shield_breaker")
        val enemy     = listOf(setOf("heavy_shields"))
        val bonus     = TraitCounterEngine.computeBonus(candidate, enemy)
        assertEquals(TraitCounterEngine.TRAIT_BONUS_PER_MATCH, bonus, 0.001f)
    }

    // ── Multiple matches accumulate but are capped ────────────────────────────

    @Test
    fun `bonus accumulates across multiple enemies`() {
        val candidate = setOf("anti_heal", "crowd_control")
        val enemies   = listOf(
            setOf("high_sustain"),
            setOf("high_mobility")
        )
        val bonus = TraitCounterEngine.computeBonus(candidate, enemies)
        val expected = TraitCounterEngine.TRAIT_BONUS_PER_MATCH * 2
        assertEquals(expected, bonus, 0.001f)
    }

    @Test
    fun `bonus is capped at MAX_TRAIT_BONUS`() {
        // Stack many matching threats
        val candidate = setOf("anti_heal", "true_damage", "crowd_control", "anti_dash", "suppress", "disengage", "shield_breaker")
        val enemies   = listOf(
            setOf("high_sustain"),
            setOf("high_armor"),
            setOf("high_mobility"),
            setOf("hard_engage"),
            setOf("heavy_shields")
        )
        val bonus = TraitCounterEngine.computeBonus(candidate, enemies)
        assertTrue("Bonus $bonus exceeds MAX", bonus <= TraitCounterEngine.MAX_TRAIT_BONUS + 0.001f)
    }

    // ── No match produces zero bonus ──────────────────────────────────────────

    @Test
    fun `irrelevant traits produce zero bonus`() {
        val candidate = setOf("poke_kite")
        val enemy     = listOf(setOf("high_sustain"))  // poke_kite does not counter high_sustain
        val bonus     = TraitCounterEngine.computeBonus(candidate, enemy)
        assertEquals(0f, bonus, 0.001f)
    }

    // ── describeCounters ──────────────────────────────────────────────────────

    @Test
    fun `describeCounters returns non-null when match found`() {
        val candidate    = setOf("anti_heal")
        val enemyTraits  = setOf("high_sustain")
        val description  = TraitCounterEngine.describeCounters(candidate, enemyTraits)
        assertNotNull(description)
        assertTrue(description!!.contains("Trait counters", ignoreCase = true))
    }

    @Test
    fun `describeCounters returns null when no match`() {
        val candidate   = setOf("poke_kite")
        val enemyTraits = setOf("high_sustain")
        assertNull(TraitCounterEngine.describeCounters(candidate, enemyTraits))
    }

    @Test
    fun `flat overload matches correctly for high_sustain vs anti_heal`() {
        val candidate   = setOf("anti_heal")
        val enemyTraits = setOf("high_sustain")
        val bonus = TraitCounterEngine.computeBonusFlat(candidate, enemyTraits)
        assertEquals(TraitCounterEngine.TRAIT_BONUS_PER_MATCH, bonus, 0.001f)
    }
}
