package com.mlbb.assistant.domain.advisor

import androidx.compose.runtime.Stable
import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero

/**
 * Recommended emblem for a hero in a given role context.
 *
 * MLBB emblems are categorised by the 2024/2025 system:
 * Mage, Assassin, Fighter, Marksman, Tank, Support, Common.
 */
@Stable
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
 * @Stable tells the Compose compiler all public fields are stable types,
 * enabling skipping recomposition when the object reference is unchanged.
 */
@Stable
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
        val isCarry  = hero.role in listOf("Marksman", "Mage")
        val isJungle = hero.lane.name == "JUNGLE"
        val isRoam   = hero.lane.name == "ROAM"

        return when {
            isJungle -> Triple("Retribution", "Flicker", "Essential for jungle control")
            isRoam   -> Triple("Flicker",     "Vengeance", "Engages and escapes for roamer")
            isCarry && enemy.mobilityLevel == MobilityLevel.HIGH ->
                Triple("Sprint", "Flicker", "Escape enemy high-mobility dive comp")
            isCarry && enemy.physicalPct > 0.7f ->
                Triple("Inspire", "Flicker", "Maximize DPS output vs physical enemy")
            hero.role == "Fighter" && enemy.ccLevel == CCLevel.HIGH ->
                Triple("Purify", "Vengeance", "Break free from heavy enemy CC")
            enemy.ccLevel == CCLevel.HIGH && hero.role in listOf("Assassin", "Mage") ->
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

        if (enemy.physicalPct >= 0.60f) {
            if (hero.role in listOf("Tank", "Fighter")) {
                if (core.none { it.name.contains("Cuirass") || it.name.contains("Dominance") }) {
                    situational.add(CoreItem(9001, "Antique Cuirass", 4))
                }
                situational.add(CoreItem(9002, "Dominance Ice", 5))
            } else if (hero.role in listOf("Marksman", "Mage", "Support")) {
                situational.add(CoreItem(9003, "Brute Force Breastplate", 4))
            }
        }
        if (enemy.magicPct >= 0.60f) {
            if (hero.role in listOf("Tank", "Fighter")) {
                situational.add(CoreItem(9004, "Athena's Shield", 4))
            } else {
                situational.add(CoreItem(9005, "Radiant Armor", 4))
            }
        }
        if (enemy.ccLevel == CCLevel.HIGH && hero.role in listOf("Marksman", "Assassin", "Mage")) {
            situational.add(CoreItem(9006, "Immortality", 5))
        }
        if (enemy.sustainLevel == SustainLevel.HIGH) {
            situational.add(CoreItem(9007, "Sea Halberd", 4))
        }
        if (enemy.mobilityLevel == MobilityLevel.HIGH && hero.role == "Marksman") {
            situational.add(CoreItem(9008, "Wind of Nature", 5))
        }

        // Deduplicate and cap at 3 situational items.
        val deduped = situational.distinctBy { it.id }.take(3)

        return Pair(core, deduped)
    }

    // ── Emblem recommendation ─────────────────────────────────────────────────

    private fun recommendEmblem(hero: Hero, enemy: CompositionProfile): EmblemRecommendation {
        return when (hero.role) {
            "Mage" -> EmblemRecommendation(
                name        = "Mage Emblem",
                tier3Talent = "Impure Rage",
                reason      = "Reduces mana cost and deals extra damage on skills"
            )
            "Assassin" -> EmblemRecommendation(
                name        = "Assassin Emblem",
                tier3Talent = "Killing Spree",
                reason      = "Heal and speed burst after securing a kill — sustain your all-in"
            )
            "Fighter" -> if (enemy.ccLevel == CCLevel.HIGH)
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
            "Marksman" -> EmblemRecommendation(
                name        = "Marksman Emblem",
                tier3Talent = "Weakness Finder",
                reason      = "Random slow on basic attacks enhances kiting and chase potential"
            )
            "Tank" -> EmblemRecommendation(
                name        = "Tank Emblem",
                tier3Talent = "Brave Smite",
                reason      = "Heals roamer on CC skills — sustain in aggressive early rotations"
            )
            "Support" -> EmblemRecommendation(
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
        if (enemy.physicalPct >= 0.70f) add("Armour items prioritised — enemy is physical-heavy")
        if (enemy.magicPct    >= 0.70f) add("Magic resist items recommended — enemy is magic-heavy")
        if (enemy.mobilityLevel == MobilityLevel.HIGH) add("Slow items help vs high-mobility enemies")
        if (hero.role == "Marksman") add("Build attack speed early for consistent DPS")
        if (enemy.sustainLevel == SustainLevel.HIGH)   add("Sea Halberd / Necklace of Durance recommended — cut enemy healing")
    }

    private fun buildMacroTips(hero: Hero, enemy: CompositionProfile): List<String> = buildList {
        if (hero.lane.name == "JUNGLE") add("Invade enemy jungle early if they have a weak early jungler")
        if (hero.lane.name == "GOLD")   add("Prioritise farm — reach item spikes before teamfights")
        if (hero.lane.name == "ROAM")   add("Rotate mid after every successful gank to maintain vision")
        if (enemy.ccLevel == CCLevel.HIGH)         add("Avoid solo engages — fight only as a group")
        if (enemy.sustainLevel == SustainLevel.HIGH) add("Use burst combos to overwhelm before they sustain")
        if (enemy.mobilityLevel == MobilityLevel.HIGH) add("Place deep vision to track high-mobility enemies")
        if (enemy.physicalPct >= 0.80f) add("Rush defensive armour on frontliners — enemy is fully physical")
    }
}
