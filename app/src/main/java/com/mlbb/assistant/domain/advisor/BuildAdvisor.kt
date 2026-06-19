package com.mlbb.assistant.domain.advisor

import androidx.compose.runtime.Stable
import com.mlbb.assistant.domain.model.CoreItem
import com.mlbb.assistant.domain.model.Hero

/**
 * Build recommendation for a single hero against a given enemy composition.
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
    val itemReasons: List<String>,
    val macroTips: List<String>
)

object BuildAdvisor {

    fun getAdvice(yourHero: Hero, enemyComp: CompositionProfile): BuildAdvice {
        val spellPair = recommendSpell(yourHero, enemyComp)
        val items     = adjustItems(yourHero, enemyComp)
        val reasons   = buildItemReasons(yourHero, enemyComp)
        val macro     = buildMacroTips(yourHero, enemyComp)

        return BuildAdvice(
            battleSpell = spellPair.first,
            altSpell    = spellPair.second,
            spellReason = spellPair.third,
            coreItems   = items,
            itemReasons = reasons,
            macroTips   = macro
        )
    }

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
            else -> {
                val primary = hero.recommendedSpells.getOrElse(0) { "Flicker" }
                val alt     = hero.recommendedSpells.getOrElse(1) { "Sprint" }
                Triple(primary, alt, "Recommended for ${hero.name}'s playstyle")
            }
        }
    }

    private fun adjustItems(hero: Hero, enemy: CompositionProfile): List<CoreItem> {
        val adjusted = hero.coreItems.toMutableList()

        if (enemy.physicalPct >= 0.70f) {
            val hasArmor = adjusted.any { it.name.contains("Cuirass") || it.name.contains("Dominance") }
            if (!hasArmor && hero.role in listOf("Tank", "Fighter")) {
                adjusted.add(0, CoreItem(999, "Antique Cuirass", 1))
                if (adjusted.size > 3) adjusted.removeAt(adjusted.lastIndex)
            }
        }
        if (enemy.magicPct >= 0.70f) {
            val hasResist = adjusted.any { it.name.contains("Oracle") || it.name.contains("Athena") }
            if (!hasResist && hero.role in listOf("Tank", "Fighter")) {
                adjusted.add(CoreItem(998, "Athena's Shield", adjusted.size + 1))
                if (adjusted.size > 3) adjusted.removeAt(adjusted.size - 2)
            }
        }
        return adjusted.take(3)
    }

    private fun buildItemReasons(hero: Hero, enemy: CompositionProfile): List<String> = buildList {
        if (enemy.physicalPct >= 0.70f) add("Armour items prioritised — enemy is physical-heavy")
        if (enemy.magicPct    >= 0.70f) add("Magic resist items recommended — enemy is magic-heavy")
        if (enemy.mobilityLevel == MobilityLevel.HIGH) add("Slow items help vs high-mobility enemies")
        if (hero.role == "Marksman") add("Build attack speed early for consistent DPS")
    }

    private fun buildMacroTips(hero: Hero, enemy: CompositionProfile): List<String> = buildList {
        if (hero.lane.name == "JUNGLE") add("Invade enemy jungle early if they have a weak early jungler")
        if (hero.lane.name == "GOLD")   add("Prioritise farm — reach item spikes before teamfights")
        if (hero.lane.name == "ROAM")   add("Rotate mid after every successful gank to maintain vision")
        if (enemy.ccLevel == CCLevel.HIGH)         add("Avoid solo engages — fight only as a group")
        if (enemy.sustainLevel == SustainLevel.HIGH) add("Use burst combos to overwhelm before they sustain")
        if (enemy.mobilityLevel == MobilityLevel.HIGH) add("Place deep vision to track high-mobility enemies")
    }
}
