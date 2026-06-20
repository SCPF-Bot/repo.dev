package com.mlbb.assistant.domain.advisor

/**
 * High-level composition archetype inferred from the heroes picked by a
 * single team.  Each archetype carries a human-readable win-condition and
 * counter-condition shown in the overlay and on the draft-complete screen.
 */
enum class CompositionArchetype(
    val display: String,
    val icon: String,
    val winCondition: String,
    val counterCondition: String
) {
    DIVE(
        display          = "Dive",
        icon             = "⚡",
        winCondition     = "Force simultaneous engages when Khufra / Atlas ults are available; never fight piecemeal",
        counterCondition = "Kite and split — do NOT cluster; deny frontline the angle to jump"
    ),
    POKE(
        display          = "Poke",
        icon             = "🎯",
        winCondition     = "Whittle enemy HP to 40 % before objectives — never all-in early",
        counterCondition = "Build HP + shields and close the gap fast to invalidate range"
    ),
    TURTLE(
        display          = "Turtle / Sustain",
        icon             = "🛡️",
        winCondition     = "Contest every Lord; force sustained fights near objectives where heals matter",
        counterCondition = "Burst them down before they set up — pick off healers first"
    ),
    WOMBO_COMBO(
        display          = "Wombo Combo",
        icon             = "💥",
        winCondition     = "Stack AoE ults and initiate as one; never fragment the combo",
        counterCondition = "Interrupt initiators with hard CC before they can chain ults"
    ),
    SPLIT_PUSH(
        display          = "Split Push",
        icon             = "🔀",
        winCondition     = "Maintain constant dual-lane pressure; force unfavourable 4v5 rotations",
        counterCondition = "Five-man collapse and end quickly; deny time for split to work"
    ),
    BALANCED(
        display          = "Balanced",
        icon             = "⚖️",
        winCondition     = "Adapt to the flow; leverage your flexible response options in every fight",
        counterCondition = "Identify the win condition and target it early — do not let them dictate pace"
    );

    companion object {

        /**
         * Derives the primary archetype from a team's [CompositionProfile].
         *
         * Heuristics (in priority order):
         * 1. 3 + assassins / high-mobility divers → DIVE
         * 2. 2 + long-range poke mages / marksmen (no sustain) → POKE
         * 3. 2 + supports / high sustain → TURTLE
         * 4. 2 + AoE ult heroes (flagged via hasCCUlt) → WOMBO_COMBO
         * 5. 1 high-mobility carry + weak teamfight → SPLIT_PUSH
         * 6. Everything else → BALANCED
         */
        fun detect(profile: CompositionProfile, heroRoles: List<String>, ccUltCount: Int): CompositionArchetype {
            val assassinCount  = heroRoles.count { it == "Assassin" }
            val supportCount   = heroRoles.count { it == "Support" }
            val tankCount      = heroRoles.count { it == "Tank" }
            val mageCount      = heroRoles.count { it == "Mage" }
            val marksmanCount  = heroRoles.count { it == "Marksman" }

            return when {
                assassinCount >= 2 && profile.mobilityLevel == MobilityLevel.HIGH -> DIVE
                (tankCount >= 2 || (tankCount >= 1 && assassinCount >= 1)) &&
                        profile.ccLevel == CCLevel.HIGH && ccUltCount >= 2 -> WOMBO_COMBO
                (mageCount + marksmanCount) >= 3 &&
                        profile.sustainLevel == SustainLevel.LOW -> POKE
                supportCount >= 2 && profile.sustainLevel != SustainLevel.LOW -> TURTLE
                assassinCount >= 1 && (tankCount + supportCount) <= 1 -> SPLIT_PUSH
                else -> BALANCED
            }
        }
    }
}
