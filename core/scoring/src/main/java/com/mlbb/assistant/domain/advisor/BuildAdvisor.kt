package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.HeroRole
import com.mlbb.assistant.domain.model.roleEnum

/**
 * Recommended emblem for a hero in a given role context.
 *
 * MLBB emblems are categorised by the 2024/2025 system:
 * Mage, Assassin, Fighter, Marksman, Tank, Support, Common.
 */
data class EmblemRecommendation(
    val name: String,
    val tier3Talent: String,
    val reason: String
)

/**
 * Build recommendation for a single hero against a given enemy composition.
 *
 * TD-02: Extended to carry up to 6 items (3 core + 3 situational) so the
 * overlay can present a full build, not just the first 3 items.
 *
 * Pure-Kotlin domain data class; Compose compiler infers stability from
 * val-only fields without a framework annotation.
 */
data class BuildAdvice(
    val battleSpell: String,
    val altSpell: String,
    val spellReason: String,
    val coreItems: List<CoreItem>,
    val situationalItems: List<CoreItem>,   // Items 4–6; adapt to enemy composition
    val itemReasons: List<String>,
    val macroTips: List<String>,
    val emblem: EmblemRecommendation
)

object BuildAdvisor {

    /**
     * P2-01 fix: magic float thresholds extracted into named constants.
     *
     * Previously `0.60f`, `0.70f`, `0.80f` appeared as bare literals in
     * [adjustItems], [buildItemReasons], and [buildMacroTips] with no
     * indication of what they meant or whether they were independent values
     * or part of a shared scale. Named constants make the intent explicit and
     * allow patch-level recalibration in a single place.
     */
    private object BuildThresholds {
        const val DAMAGE_MODERATE = 0.60f   // ≥ 60 % of a type → consider defensive items
        const val DAMAGE_HEAVY    = 0.70f   // ≥ 70 % → prioritise typed resistance
        const val DAMAGE_FULL     = 0.80f   // ≥ 80 % → rush frontliner armour/MR immediately
    }

    fun getAdvice(yourHero: Hero, enemyComp: CompositionProfile): BuildAdvice {
        val spellPair        = recommendSpell(yourHero, enemyComp)
        val (core, situational) = adjustItems(yourHero, enemyComp)
        val reasons          = buildItemReasons(yourHero, enemyComp)
        val macro            = buildMacroTips(yourHero, enemyComp)
        val emblem           = recommendEmblem(yourHero, enemyComp)

        return BuildAdvice(
            battleSpell      = spellPair.first,
            altSpell         = spellPair.second,
            spellReason      = spellPair.third,
            coreItems        = core,
            situationalItems = situational,
            itemReasons      = reasons,
            macroTips        = macro,
            emblem           = emblem
        )
    }

    // ── Spell recommendation ──────────────────────────────────────────────────

    private fun recommendSpell(hero: Hero, enemy: CompositionProfile): Triple<String, String, String> {
        val isCarry  = hero.roleEnum in setOf(HeroRole.MARKSMAN, HeroRole.MAGE)
        val isJungle = hero.lane.name == "JUNGLE"
        val isRoam   = hero.lane.name == "ROAM"

        return when {
            isJungle -> Triple("Retribution", "Flicker", "Essential for jungle control")
            isRoam   -> Triple("Flicker",     "Vengeance", "Engages and escapes for roamer")
            isCarry && enemy.mobilityLevel == MobilityLevel.HIGH ->
                Triple("Sprint", "Flicker", "Escape enemy high-mobility dive comp")
            isCarry && enemy.physicalPct > BuildThresholds.DAMAGE_HEAVY ->
                Triple("Inspire", "Flicker", "Maximize DPS output vs physical enemy")
            hero.roleEnum == HeroRole.FIGHTER && enemy.ccLevel == CCLevel.HIGH ->
                Triple("Purify", "Vengeance", "Break free from heavy enemy CC")
            enemy.ccLevel == CCLevel.HIGH && hero.roleEnum in setOf(HeroRole.ASSASSIN, HeroRole.MAGE) ->
                Triple("Purify", "Flicker", "Escape CC chains to complete your combo")
            else -> {
                val primary = hero.recommendedSpells.getOrElse(0) { "Flicker" }
                val alt     = hero.recommendedSpells.getOrElse(1) { "Sprint" }
                Triple(primary, alt, "Recommended for ${hero.name}'s playstyle")
            }
        }
    }

    // ── Item recommendation ───────────────────────────────────────────────────

    /**
     * Returns a pair of (coreItems, situationalItems).
     *
     * Core items (up to 3) are always built.
     * Situational items (up to 3) adapt to the enemy composition.
     */
    private fun adjustItems(hero: Hero, enemy: CompositionProfile): Pair<List<CoreItem>, List<CoreItem>> {
        val core = hero.coreItems.take(3).toMutableList()
        val situational = mutableListOf<CoreItem>()

        // ── Defensive situational items ────────────────────────────────────────

        if (enemy.physicalPct >= BuildThresholds.DAMAGE_MODERATE) {
            if (hero.roleEnum in setOf(HeroRole.TANK, HeroRole.FIGHTER)) {
                if (core.none { it.name.contains("Cuirass") || it.name.contains("Dominance") }) {
                    situational.add(CoreItem(9001, "Antique Cuirass", 4))
                }
                situational.add(CoreItem(9002, "Dominance Ice", 5))
            } else if (hero.roleEnum in setOf(HeroRole.MARKSMAN, HeroRole.MAGE, HeroRole.SUPPORT)) {
                situational.add(CoreItem(9003, "Brute Force Breastplate", 4))
            }
        }
        if (enemy.magicPct >= BuildThresholds.DAMAGE_MODERATE) {
            if (hero.roleEnum in setOf(HeroRole.TANK, HeroRole.FIGHTER)) {
                situational.add(CoreItem(9004, "Athena's Shield", 4))
            } else {
                situational.add(CoreItem(9005, "Radiant Armor", 4))
            }
        }
        if (enemy.ccLevel == CCLevel.HIGH && hero.roleEnum in setOf(HeroRole.MARKSMAN, HeroRole.ASSASSIN, HeroRole.MAGE)) {
            situational.add(CoreItem(9006, "Immortality", 5))
        }
        if (enemy.sustainLevel == SustainLevel.HIGH) {
            situational.add(CoreItem(9007, "Sea Halberd", 4))
        }
        if (enemy.mobilityLevel == MobilityLevel.HIGH && hero.roleEnum == HeroRole.MARKSMAN) {
            situational.add(CoreItem(9008, "Wind of Nature", 5))
        }

        // Deduplicate and cap at 3 situational items.
        val deduped = situational.distinctBy { it.id }.take(3)

        return Pair(core, deduped)
    }

    // ── Emblem recommendation ─────────────────────────────────────────────────

    private fun recommendEmblem(hero: Hero, enemy: CompositionProfile): EmblemRecommendation {
        return when (hero.roleEnum) {
            HeroRole.MAGE -> EmblemRecommendation(
                name        = "Mage Emblem",
                tier3Talent = "Impure Rage",
                reason      = "Reduces mana cost and deals extra damage on skills"
            )
            HeroRole.ASSASSIN -> EmblemRecommendation(
                name        = "Assassin Emblem",
                tier3Talent = "Killing Spree",
                reason      = "Heal and speed burst after securing a kill — sustain your all-in"
            )
            HeroRole.FIGHTER -> if (enemy.ccLevel == CCLevel.HIGH)
                EmblemRecommendation(
                    name        = "Fighter Emblem",
                    tier3Talent = "Persistence",
                    reason      = "Bonus HP and physical lifesteal to survive heavy CC poke"
                )
            else
                EmblemRecommendation(
                    name        = "Fighter Emblem",
                    tier3Talent = "Festival of Blood",
                    reason      = "Spellvamp stacks enable sustained trading in EXP lane"
                )
            HeroRole.MARKSMAN -> EmblemRecommendation(
                name        = "Marksman Emblem",
                tier3Talent = "Weakness Finder",
                reason      = "Random slow on basic attacks enhances kiting and chase potential"
            )
            HeroRole.TANK -> EmblemRecommendation(
                name        = "Tank Emblem",
                tier3Talent = "Brave Smite",
                reason      = "Heals roamer on CC skills — sustain in aggressive early rotations"
            )
            HeroRole.SUPPORT -> EmblemRecommendation(
                name        = "Support Emblem",
                tier3Talent = "Focusing Mark",
                reason      = "Amplifies ally damage on marked targets — boost carry output"
            )
            else -> EmblemRecommendation(
                name        = "Common Emblem",
                tier3Talent = "Inspire",
                reason      = "Flexible talent for non-standard role combinations"
            )
        }
    }

    // ── Reason strings ────────────────────────────────────────────────────────

    private fun buildItemReasons(hero: Hero, enemy: CompositionProfile): List<String> = buildList {
        if (enemy.physicalPct >= BuildThresholds.DAMAGE_HEAVY) add("Armour items prioritised — enemy is physical-heavy")
        if (enemy.magicPct    >= BuildThresholds.DAMAGE_HEAVY) add("Magic resist items recommended — enemy is magic-heavy")
        if (enemy.mobilityLevel == MobilityLevel.HIGH) add("Slow items help vs high-mobility enemies")
        if (hero.roleEnum == HeroRole.MARKSMAN) add("Build attack speed early for consistent DPS")
        if (enemy.sustainLevel == SustainLevel.HIGH)   add("Sea Halberd / Necklace of Durance recommended — cut enemy healing")
    }

    private fun buildMacroTips(hero: Hero, enemy: CompositionProfile): List<String> = buildList {
        if (hero.lane.name == "JUNGLE") add("Invade enemy jungle early if they have a weak early jungler")
        if (hero.lane.name == "GOLD")   add("Prioritise farm — reach item spikes before teamfights")
        if (hero.lane.name == "ROAM")   add("Rotate mid after every successful gank to maintain vision")
        if (enemy.ccLevel == CCLevel.HIGH)           add("Avoid solo engages — fight only as a group")
        if (enemy.sustainLevel == SustainLevel.HIGH) add("Use burst combos to overwhelm before they sustain")
        if (enemy.mobilityLevel == MobilityLevel.HIGH) add("Place deep vision to track high-mobility enemies")
        if (enemy.physicalPct >= BuildThresholds.DAMAGE_FULL) add("Rush defensive armour on frontliners — enemy is fully physical")
    }
}
