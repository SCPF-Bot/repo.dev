package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Lane
import com.mlbb.assistant.domain.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildAdvisorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeHero(
        id: Int = 1,
        name: String = "TestHero",
        role: String,
        lane: Lane = Lane.MID,
        coreItems: List<CoreItem> = listOf(
            CoreItem(1, "Item A", 1),
            CoreItem(2, "Item B", 2),
            CoreItem(3, "Item C", 3),
            CoreItem(4, "Item D", 4)
        ),
        recommendedSpells: List<String> = listOf("Flicker", "Sprint")
    ) = Hero(
        id                = id,
        name              = name,
        role              = role,
        secondaryRole     = null,
        lane              = lane,
        tier              = Tier.A,
        patchTrend        = 0.0,
        winRate           = 0.50,
        pickRate          = 0.10,
        banRate           = 0.10,
        imageUrl          = "",
        counters          = emptyList(),
        counteredBy       = emptyList(),
        synergies         = emptyList(),
        recommendedSpells = recommendedSpells,
        coreItems         = coreItems,
        flexLanes         = emptyList(),
        isToxicMechanic   = false,
        isOP              = false
    )

    private fun neutralComp(
        physicalPct: Float   = 0.5f,
        magicPct: Float      = 0.5f,
        ccLevel: CCLevel     = CCLevel.LOW,
        mobilityLevel: MobilityLevel = MobilityLevel.LOW,
        sustainLevel: SustainLevel   = SustainLevel.LOW
    ) = CompositionProfile(physicalPct, magicPct, ccLevel, mobilityLevel, sustainLevel, emptyList())

    // ── Spell recommendation ──────────────────────────────────────────────────

    @Test
    fun `jungle heroes always get Retribution as battle spell`() {
        val hero   = makeHero(role = "Fighter", lane = Lane.JUNGLE)
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertEquals("Retribution", advice.battleSpell)
    }

    @Test
    fun `roam heroes always get Flicker as battle spell`() {
        val hero   = makeHero(role = "Support", lane = Lane.ROAM)
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertEquals("Flicker", advice.battleSpell)
    }

    @Test
    fun `carry against high mobility enemies gets Sprint to escape`() {
        val hero   = makeHero(role = "Marksman", lane = Lane.GOLD)
        val advice = BuildAdvisor.getAdvice(hero, neutralComp(mobilityLevel = MobilityLevel.HIGH))
        assertEquals("Sprint", advice.battleSpell)
    }

    @Test
    fun `fighter facing heavy CC gets Purify`() {
        val hero   = makeHero(role = "Fighter", lane = Lane.EXP)
        val advice = BuildAdvisor.getAdvice(hero, neutralComp(ccLevel = CCLevel.HIGH))
        assertEquals("Purify", advice.battleSpell)
    }

    @Test
    fun `falls back to hero recommended spells when no special case matches`() {
        val hero   = makeHero(role = "Tank", lane = Lane.EXP, recommendedSpells = listOf("Aegis", "Revitalize"))
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertEquals("Aegis", advice.battleSpell)
        assertEquals("Revitalize", advice.altSpell)
    }

    // ── Item recommendation ────────────────────────────────────────────────────

    @Test
    fun `core items are capped at 3 regardless of hero item pool size`() {
        val hero   = makeHero(role = "Marksman")
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertTrue(advice.coreItems.size <= 3)
    }

    @Test
    fun `situational items are capped at 3 even with many triggered conditions`() {
        val hero = makeHero(role = "Marksman")
        val comp = CompositionProfile(
            physicalPct   = 0.9f,
            magicPct      = 0.9f,
            ccLevel       = CCLevel.HIGH,
            mobilityLevel = MobilityLevel.HIGH,
            sustainLevel  = SustainLevel.HIGH,
            warnings      = emptyList()
        )
        val advice = BuildAdvisor.getAdvice(hero, comp)
        assertTrue(advice.situationalItems.size <= 3)
    }

    @Test
    fun `no situational items are suggested against a balanced enemy comp`() {
        val hero   = makeHero(role = "Marksman")
        val advice = BuildAdvisor.getAdvice(hero, neutralComp(physicalPct = 0.5f, magicPct = 0.5f))
        assertTrue(advice.situationalItems.isEmpty())
    }

    @Test
    fun `tank vs heavy physical comp gets Antique Cuirass style defensive items`() {
        val hero   = makeHero(role = "Tank", coreItems = emptyList())
        val advice = BuildAdvisor.getAdvice(hero, neutralComp(physicalPct = 0.8f))
        assertTrue(advice.situationalItems.any { it.name.contains("Cuirass") })
    }

    // ── Emblem recommendation ──────────────────────────────────────────────────

    @Test
    fun `mage role always recommends the Mage Emblem`() {
        val hero   = makeHero(role = "Mage")
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertEquals("Mage Emblem", advice.emblem.name)
    }

    @Test
    fun `fighter emblem talent changes when facing heavy CC`() {
        val hero        = makeHero(role = "Fighter")
        val underCC     = BuildAdvisor.getAdvice(hero, neutralComp(ccLevel = CCLevel.HIGH)).emblem
        val noCC        = BuildAdvisor.getAdvice(hero, neutralComp(ccLevel = CCLevel.LOW)).emblem
        assertEquals("Persistence", underCC.tier3Talent)
        assertEquals("Festival of Blood", noCC.tier3Talent)
    }

    @Test
    fun `unrecognised role falls back to Common Emblem`() {
        val hero   = makeHero(role = "Jungler")
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertEquals("Common Emblem", advice.emblem.name)
    }

    // ── Macro tips ───────────────────────────────────────────────────────────

    @Test
    fun `jungle lane heroes get an invade tip`() {
        val hero   = makeHero(role = "Fighter", lane = Lane.JUNGLE)
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertTrue(advice.macroTips.any { it.contains("Invade") })
    }

    @Test
    fun `roam lane heroes get a rotate-and-vision tip`() {
        val hero   = makeHero(role = "Support", lane = Lane.ROAM)
        val advice = BuildAdvisor.getAdvice(hero, neutralComp())
        assertTrue(advice.macroTips.any { it.contains("Rotate mid") })
    }
}
