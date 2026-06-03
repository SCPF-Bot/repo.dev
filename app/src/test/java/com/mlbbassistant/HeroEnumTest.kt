package com.mlbbassistant

import com.mlbbassistant.data.model.HeroLane
import com.mlbbassistant.data.model.HeroRole
import org.junit.Assert.*
import org.junit.Test

class HeroEnumTest {

    @Test
    fun `HeroRole fromString is case-insensitive`() {
        assertEquals(HeroRole.TANK,     HeroRole.fromString("tank"))
        assertEquals(HeroRole.TANK,     HeroRole.fromString("TANK"))
        assertEquals(HeroRole.ASSASSIN, HeroRole.fromString("Assassin"))
    }

    @Test
    fun `HeroRole fromString returns UNKNOWN for unrecognised value`() {
        assertEquals(HeroRole.UNKNOWN, HeroRole.fromString("invalid_role"))
        assertEquals(HeroRole.UNKNOWN, HeroRole.fromString(""))
    }

    @Test
    fun `HeroLane fromString is case-insensitive`() {
        assertEquals(HeroLane.GOLD,   HeroLane.fromString("gold"))
        assertEquals(HeroLane.GOLD,   HeroLane.fromString("GOLD"))
        assertEquals(HeroLane.JUNGLE, HeroLane.fromString("Jungle"))
    }

    @Test
    fun `HeroLane fromString falls back to JUNGLE for unknown values`() {
        assertEquals(HeroLane.JUNGLE, HeroLane.fromString("unknown_lane"))
    }

    @Test
    fun `all HeroRole entries have non-blank displayName`() {
        HeroRole.entries.forEach { role ->
            assertTrue("${role.name} has blank displayName", role.displayName.isNotBlank())
        }
    }

    @Test
    fun `all HeroLane entries have non-blank displayName`() {
        HeroLane.entries.forEach { lane ->
            assertTrue("${lane.name} has blank displayName", lane.displayName.isNotBlank())
        }
    }
}
