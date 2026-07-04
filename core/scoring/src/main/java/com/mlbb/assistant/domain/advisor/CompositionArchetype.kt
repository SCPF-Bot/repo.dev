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
         * Priority order (highest → lowest):
         * 1. WOMBO_COMBO — 2+ heroes with CC ults AND high CC level overall.
         *    Checked first because a WOMBO_COMBO comp often also has assassins/divers,
         *    and the combo identity is the defining win-condition.
         * 2. DIVE — 2+ assassins with high team mobility.
         * 3. POKE — 3+ ranged carries (mages + marksmen) with low sustain.
         * 4. TURTLE — 2+ supports with meaningful sustain.
         * 5. SPLIT_PUSH — at least one assassin/fighter with minimal frontline (0–1 tank/support).
         * 6. BALANCED — everything else.
         */
        fun detect(profile: CompositionProfile, heroRoles: List<String>, ccUltCount: Int): CompositionArchetype {
            val assassinCount  = heroRoles.count { it == "Assassin" }
            val supportCount   = heroRoles.count { it == "Support" }
            val tankCount      = heroRoles.count { it == "Tank" }
            val mageCount      = heroRoles.count { it == "Mage" }
            val marksmanCount  = heroRoles.count { it == "Marksman" }

            return when {
                // Wombo-combo: hard CC ults + sufficient frontline to enable the chain.
                ccUltCount >= 2 && profile.ccLevel == CCLevel.HIGH &&
                        (tankCount + supportCount) >= 1 -> WOMBO_COMBO
                // Dive: assassin-heavy with team-wide high mobility.
                assassinCount >= 2 && profile.mobilityLevel == MobilityLevel.HIGH -> DIVE
                // Poke: 3+ ranged damage dealers with no meaningful sustain.
                (mageCount + marksmanCount) >= 3 &&
                        profile.sustainLevel == SustainLevel.LOW -> POKE
                // Turtle / sustain: double support backed by sustain.
                supportCount >= 2 && profile.sustainLevel != SustainLevel.LOW -> TURTLE
                // Split push: mobile carry with virtually no frontline.
                assassinCount >= 1 && (tankCount + supportCount) <= 1 -> SPLIT_PUSH
                else -> BALANCED
            }
        }
    }
}
