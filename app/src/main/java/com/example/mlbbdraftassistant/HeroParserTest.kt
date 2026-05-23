package com.example.mlbbdraftassistant

import com.example.mlbbdraftassistant.util.HeroParser
import org.junit.Assert.*
import org.junit.Test

class HeroParserTest {

    private val sampleJson = """
        [
            {
                "hero_id": 1,
                "hero_name": "Layla",
                "hero_role": "Marksman",
                "hero_image": "https://example.com/layla.png",
                "hero_counters": [
                    { "hero_id": 2, "counter_strength": 4.5 }
                ],
                "hero_synergies": [
                    { "hero_id": 3, "synergy_strength": 3.2 }
                ],
                "win_rate": 51.2,
                "pick_rate": 12.3,
                "ban_rate": 5.1
            },
            {
                "hero_id": 2,
                "hero_name": "Saber",
                "hero_role": "Assassin",
                "hero_image": "https://example.com/saber.png",
                "hero_counters": [],
                "hero_synergies": [],
                "win_rate": 48.7,
                "pick_rate": 8.9,
                "ban_rate": 2.3
            }
        ]
    """.trimIndent()

    @Test
    fun parse_validJson_returnsHeroList() {
        val heroes = HeroParser.parse(sampleJson)
        assertNotNull(heroes)
        assertEquals(2, heroes!!.size)

        val layla = heroes[0]
        assertEquals("Layla", layla.hero_name)
        assertEquals("Marksman", layla.hero_role)
        assertEquals(51.2f, layla.win_rate)
        assertEquals(1, layla.hero_counters?.size)
        assertEquals(4.5f, layla.hero_counters?.get(0)?.counter_strength)
        assertEquals("layla", layla.normalizedName)
    }

    @Test
    fun normalizeName_removesSpacesAndSpecialChars() {
        val hero = Hero(1, " Chou  ", "Fighter", null, null, null, null, null, null)
        assertEquals("chou", hero.normalizedName)
    }
}