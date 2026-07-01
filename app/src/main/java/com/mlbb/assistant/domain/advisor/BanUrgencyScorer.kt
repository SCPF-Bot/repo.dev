package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero

/**
 * Scores how urgently a hero should be banned given the current draft context.
 *
 * This is the "urgency" half of the ban value vs urgency split outlined in
 * roadmap item A4.  Unlike [BanValueScorer] (context-free), urgency is purely
 * reactive — it answers: "given what we already know about both teams, how
 * critical is it to remove this hero from the pool *right now*?"
 *
 * ## Urgency sources
 * 1. **Direct counter threat** — hero directly counters one of our allied picks.
 *    +[COUNTER_THREAT_BONUS] per ally countered.
 * 2. **Enemy synergy amplifier** — hero synergises with an already-picked enemy.
 *    +[ENEMY_SYNERGY_BONUS] per synergy pair.
 * 3. **Archetype enabler** — hero enables or anchors the inferred enemy archetype.
 *    +[ARCHETYPE_ENABLER_BONUS] when the hero's role list aligns with the
 *    enemy archetype's primary role need.
 *
 * Urgency scores are combined with value scores inside [BanRecommender.rankSplit]
 * to produce the final per-hero ban priority.
 */
object BanUrgencyScorer {

    private const val COUNTER_THREAT_BONUS  = 0.25f
    private const val ENEMY_SYNERGY_BONUS   = 0.20f
    private const val ARCHETYPE_ENABLER_BONUS = 0.10f

    /**
     * Computes the contextual ban urgency for [hero].
     *
     * @param hero          Hero being evaluated as a ban candidate.
     * @param alliedPicks   Heroes already picked by the local (friendly) team.
     * @param enemyPicks    Heroes already picked by the enemy team.
     * @param enemyArchetype  Inferred enemy composition archetype, if available.
     *
     * @return Raw urgency score (can exceed 1.0 when all three sources fire).
     *         The caller ([BanRecommender]) combines this with [BanValueScorer.score]
     *         and clamps the result to [0, 1].
     */
    fun score(
        hero: Hero,
        alliedPicks: List<Hero>,
        enemyPicks: List<Hero>,
        enemyArchetype: CompositionArchetype? = null
    ): Float {
        var urgency = 0f

        // 1. Direct counter threat: does this hero hard-counter any of our picks?
        val counteringAllyCount = alliedPicks.count { ally -> hero.id in ally.counteredBy }
        urgency += counteringAllyCount * COUNTER_THREAT_BONUS

        // 2. Enemy synergy amplifier: does this hero synergize with enemy picks?
        val enemySynergyCount = enemyPicks.count { ep -> hero.id in ep.synergies }
        urgency += enemySynergyCount * ENEMY_SYNERGY_BONUS

        // 3. Archetype enabler: does this hero anchor the enemy's inferred comp?
        if (enemyArchetype != null && isArchetypeEnabler(hero, enemyArchetype)) {
            urgency += ARCHETYPE_ENABLER_BONUS
        }

        return urgency
    }

    /**
     * Returns a human-readable urgency reason for [hero] given the current context.
     * Suitable for display in the ban suggestion card's reason field.
     */
    fun buildReason(
        hero: Hero,
        alliedPicks: List<Hero>,
        enemyPicks: List<Hero>
    ): String? {
        val counteringAlly = alliedPicks.firstOrNull { ally -> hero.id in ally.counteredBy }
        val synergyEnemy   = enemyPicks.firstOrNull  { ep   -> hero.id in ep.synergies }

        return when {
            counteringAlly != null ->
                "Directly counters your ${counteringAlly.name} — reactive ban priority"
            synergyEnemy != null ->
                "Synergizes with enemy ${synergyEnemy.name} — deny the combo"
            else -> null
        }
    }

    /**
     * Returns true if [hero]'s primary role aligns with the key role that the
     * [archetype] depends on.  Used to identify heroes that would complete or
     * anchor the enemy's inferred composition.
     */
    private fun isArchetypeEnabler(hero: Hero, archetype: CompositionArchetype): Boolean {
        return when (archetype) {
            CompositionArchetype.WOMBO_COMBO -> hero.hasCCUlt && hero.role in listOf("Tank", "Support")
            CompositionArchetype.DIVE        -> hero.role == "Assassin"
            CompositionArchetype.POKE        -> hero.role in listOf("Mage", "Marksman")
            CompositionArchetype.TURTLE      -> hero.role == "Support"
            CompositionArchetype.SPLIT_PUSH  -> hero.role in listOf("Fighter", "Assassin")
            CompositionArchetype.BALANCED    -> false
        }
    }
}
