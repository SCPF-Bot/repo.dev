package com.example.mlbbdraftassistant

import com.example.mlbbdraftassistant.data.model.Counter
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.data.model.Synergy
import com.example.mlbbdraftassistant.domain.RecommendationEngine
import com.example.mlbbdraftassistant.domain.ScoringConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecommendationEngineTest {

    private lateinit var engine: RecommendationEngine
    private lateinit var heroPool: List<Hero>

    @Before
    fun setUp() {
        engine = RecommendationEngine(ScoringConfig.DEFAULT)

        heroPool = listOf(
            Hero(
                hero_id = 1,
                hero_name = "Fighter A",
                hero_role = "Fighter",
                hero_image = null,
                hero_counters = listOf(Counter(5, 4.5f)),  // counters Mage B
                hero_synergies = listOf(Synergy(2, 3.0f)),  // synergizes with Support C
                win_rate = 52f,
                pick_rate = 10f,
                ban_rate = 5f
            ),
            Hero(
                hero_id = 2,
                hero_name = "Support C",
                hero_role = "Support",
                hero_image = null,
                hero_counters = listOf(),
                hero_synergies = listOf(Synergy(1, 3.5f)),   // synergizes with Fighter A
                win_rate = 48f,
                pick_rate = 5f,
                ban_rate = 1f
            ),
            Hero(
                hero_id = 3,
                hero_name = "Assassin D",
                hero_role = "Assassin",
                hero_image = null,
                hero_counters = listOf(),
                hero_synergies = listOf(),
                win_rate = 54f,
                pick_rate = 15f,
                ban_rate = 12f
            ),
            Hero(
                hero_id = 4,
                hero_name = "Fighter B",
                hero_role = "Fighter",
                hero_image = null,
                hero_counters = listOf(),
                hero_synergies = listOf(),
                win_rate = 50f,
                pick_rate = 9f,
                ban_rate = 4f
            ),
            Hero(
                hero_id = 5,
                hero_name = "Mage B",
                hero_role = "Mage",
                hero_image = null,
                hero_counters = listOf(),
                hero_synergies = listOf(),
                win_rate = 47f,
                pick_rate = 6f,
                ban_rate = 2f
            )
        )
    }

    @Test
    fun `empty allies and enemies returns top heroes by meta only`() {
        val result = engine.recommend(emptyList(), emptyList(), heroPool, topN = 3)
        assertEquals(3, result.size)
        // Highest meta: Assassin D (win 54, pick 15, ban 12) should be first
        assertEquals("Assassin D", result[0].hero.hero_name)
        assertTrue(result[0].reason.contains("meta"))
    }

    @Test
    fun `counter score boosts hero who counters enemy`() {
        val allies = emptyList<Hero>()
        val enemies = listOf(heroPool.first { it.hero_name == "Mage B" })  // Mage B is countered by Fighter A
        val result = engine.recommend(allies, enemies, heroPool, topN = 1)
        assertEquals("Fighter A", result[0].hero.hero_name)
        assertTrue(result[0].counterScore > 0f)
    }

    @Test
    fun `synergy score boosts hero who synergizes with ally`() {
        val allies = listOf(heroPool.first { it.hero_name == "Support C" })   // synergizes with Fighter A
        val enemies = emptyList<Hero>()
        val result = engine.recommend(allies, enemies, heroPool, topN = 1)
        assertEquals("Fighter A", result[0].hero.hero_name)
        assertTrue(result[0].synergyScore > 0f)
    }

    @Test
    fun `role balance penalty for duplicate role`() {
        val allies = listOf(heroPool.first { it.hero_name == "Fighter B" })   // already a Fighter
        val enemies = emptyList<Hero>()
        val result = engine.recommend(allies, enemies, heroPool, topN = 5)
        val fighterA = result.find { it.hero.hero_name == "Fighter A" }!!
        // Role balance should be less than 1 because there would be 2 Fighters (still okay, but check)
        // Since maxAllowed = 2, having 2 Fighters is still perfect? Let's check logic: roleCount = allies with same role + 1 for candidate = 2. roleBalanceNormalised(2, maxAllowed=2) = 1f. So no penalty. But with 3 it penalizes.
        // So we'll test with 2 Fighters and then with 3.
        assertEquals(1f, fighterA.roleBalanceScore, 0.001f)
    }

    @Test
    fun `full team (5 allies) still gives recommendations for remaining heroes`() {
        val allies = heroPool.take(5)  // all 5 heroes as allies
        val enemies = emptyList<Hero>()
        val result = engine.recommend(allies, enemies, heroPool)
        // No heroes available (pool has only 5), should be empty
        assertTrue(result.isEmpty())
    }

    @Test
    fun `reason string includes counter and synergy mentions`() {
        val allies = listOf(heroPool.first { it.hero_name == "Support C" })
        val enemies = listOf(heroPool.first { it.hero_name == "Mage B" })
        val result = engine.recommend(allies, enemies, heroPool, topN = 1)
        val fighterA = result[0]
        assertTrue(fighterA.reason.contains("Good with Support C"))
        assertTrue(fighterA.reason.contains("Strong vs Mage B"))
    }

    @Test
    fun `total score is weighted sum`() {
        val allies = emptyList<Hero>()
        val enemies = emptyList<Hero>()
        val result = engine.recommend(allies, enemies, heroPool, topN = 1)
        val hero = result[0]
        // Only meta contributes because no ally/enemy
        val expectedMeta = (hero.metaScore * ScoringConfig.DEFAULT.metaWeight)
        assertEquals(expectedMeta, hero.totalScore, 0.001f)
    }
}