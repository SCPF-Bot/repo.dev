package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero

/**
 * Generates a concise win-condition string for a completed allied composition.
 *
 * The generator looks at the detected [CompositionArchetype], the heroes
 * that make up the team, and any notable missing role slots to produce an
 * actionable sentence displayed on the draft-complete overlay card.
 *
 * Each archetype output contains two clauses:
 *  1. What to do (the win condition).
 *  2. What to avoid (the common mistake that loses the game).
 */
object WinConditionGenerator {

    /**
     * Returns a win-condition string for [allies].
     *
     * @param allies     All five (or fewer) allied hero picks.
     * @param archetype  Archetype detected from the composition — if null the
     *                   function falls back to a generic statement.
     */
    fun generate(allies: List<Hero>, archetype: CompositionArchetype?): String {
        if (allies.isEmpty()) return "Complete the draft to see your win condition"

        val profile              = CompositionAnalyzer.analyze(allies)
        val keyHeroes            = allies.take(3).joinToString(", ") { it.name }
        val effectiveArchetype   = archetype ?: CompositionArchetype.BALANCED

        return buildString {
            append(effectiveArchetype.icon)
            append(" ")

            when (effectiveArchetype) {
                CompositionArchetype.DIVE -> {
                    val diver     = allies.firstOrNull { it.role == "Assassin" || it.role == "Fighter" }
                    val initiator = allies.firstOrNull { it.role == "Tank" }
                    if (initiator != null && diver != null) {
                        append("${initiator.name} initiates → ${diver.name} cleans up. ")
                        append("Fight at Level 4 power spike. ")
                    } else {
                        append(effectiveArchetype.winCondition)
                        append(" ")
                    }
                    append("Avoid: fragmenting your dive — commit as one or not at all.")
                }

                CompositionArchetype.POKE -> {
                    val poker = allies.firstOrNull { it.role == "Mage" || it.role == "Marksman" }
                    if (poker != null) {
                        append("Poke with ${poker.name} until enemies are below 50% HP, then push objectives. ")
                    } else {
                        append(effectiveArchetype.winCondition)
                        append(" ")
                    }
                    append("Avoid: early all-in fights before the enemy is sufficiently weakened.")
                }

                CompositionArchetype.TURTLE -> {
                    val support = allies.firstOrNull { it.role == "Support" }
                    if (support != null) {
                        append("Group around ${support.name}'s heals. Contest every Lord and secure Turtle chains. ")
                    } else {
                        append(effectiveArchetype.winCondition)
                        append(" ")
                    }
                    append("Avoid: early solo picks or engages away from allied vision.")
                }

                CompositionArchetype.WOMBO_COMBO -> {
                    val ultHeroes = allies.filter { it.hasCCUlt }.take(2)
                    if (ultHeroes.size >= 2) {
                        append("Chain ${ultHeroes[0].name} → ${ultHeroes[1].name} ults. ")
                        append("Never engage until BOTH ults are ready. ")
                    } else if (ultHeroes.size == 1) {
                        append("Build combo around ${ultHeroes[0].name}'s ult. All five engage simultaneously. ")
                    } else {
                        append(effectiveArchetype.winCondition)
                        append(" ")
                    }
                    append("Avoid: engaging piecemeal — a fragmented combo wins nothing.")
                }

                CompositionArchetype.SPLIT_PUSH -> {
                    val splitter = allies.firstOrNull { it.role == "Fighter" || it.role == "Assassin" }
                    if (splitter != null) {
                        append("${splitter.name} splits a side lane. 4-man apply pressure mid. ")
                        append("Never let them regroup. ")
                    } else {
                        append(effectiveArchetype.winCondition)
                        append(" ")
                    }
                    append("Avoid: grouping for a 5v5 — that plays into a stronger teamfight comp.")
                }

                CompositionArchetype.BALANCED -> {
                    if (profile.ccLevel == CCLevel.HIGH)
                        append("High CC comp ($keyHeroes). Force 5v5 fights — your CC chain is the win condition. ")
                    else
                        append("Flexible comp ($keyHeroes). Read the game — adapt playstyle to the situation. ")
                    append("Avoid: committing to one strategy too early when your strength is adaptability.")
                }
            }
        }
    }
}
