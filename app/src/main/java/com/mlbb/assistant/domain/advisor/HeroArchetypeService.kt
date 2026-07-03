package com.mlbb.assistant.domain.advisor

import android.content.Context
import com.mlbb.assistant.domain.model.Hero
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that loads and queries [hero_archetypes.json] — a rich archetype +
 * trait dataset sourced from community research across multiple open-source
 * MLBB draft assistants.
 *
 * Provided data classifies every hero into one or more fine-grained sub-role
 * archetypes (e.g. "Tank - Glorious Setter", "Fighter - Juggernaut") and
 * assigns trait tags (e.g. "high_sustain", "crowd_control", "anti_heal").
 *
 * Used by:
 *  - [TraitCounterEngine] — cross-referencing enemy traits for pick bonuses
 *  - [CompositionAnalyzer] — gap detection (magic damage, frontline)
 *  - [BanValueScorer] / [BanUrgencyScorer] — archetype-aware ban logic
 *
 * The JSON is parsed once on first access; subsequent calls hit an in-memory
 * cache.  Thread-safe: all mutations happen inside [ensureLoaded] which is
 * called from the main thread during app init.
 */
@Singleton
class HeroArchetypeService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    // ── Data model ────────────────────────────────────────────────────────────

    data class ArchetypeMatchupRules(
        val goodAgainst: List<String>,
        val badAgainst: List<String>
    )

    // ── Internal cache ────────────────────────────────────────────────────────

    private var heroArchetypes: Map<String, List<String>> = emptyMap()     // hero → archetypes
    private var archetypeHeroes: Map<String, List<String>> = emptyMap()   // archetype → heroes
    private var heroTraits: Map<String, Set<String>> = emptyMap()          // hero → traits
    private var matchupRules: Map<String, ArchetypeMatchupRules> = emptyMap()
    private var loaded = false

    // Magic-damage archetypes (ported from AlanNobita scoring.py)
    private val magicArchetypes = setOf(
        "Mage - Burst Mage", "Mage - Battlemage",
        "Mage - Control Mage", "Mage - DPS Mage"
    )

    // Frontline/protector archetypes
    private val protectorArchetypes = setOf(
        "Tank - Vanguard", "Tank - Stone Wall", "Tank - Glorious Setter"
    )

    // Glass-cannon archetypes (squishy high-damage)
    private val glassCannonArchetypes = setOf(
        "Marksman - Crit based", "Mage - Burst Mage", "Assassin - Prey Hunter"
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all sub-role archetypes for [heroName], e.g.
     * `["Fighter - Juggernaut", "Fighter - Berserker"]`.
     */
    fun getArchetypes(heroName: String): List<String> {
        ensureLoaded()
        return heroArchetypes[heroName] ?: emptyList()
    }

    /**
     * Returns the set of trait tags for [heroName], e.g.
     * `{"high_sustain", "crowd_control"}`.
     */
    fun getTraits(heroName: String): Set<String> {
        ensureLoaded()
        return heroTraits[heroName] ?: emptySet()
    }

    /**
     * Returns the archetype matchup rules for [archetype], listing which
     * other archetypes it counters and which counter it.
     */
    fun getMatchupRules(archetype: String): ArchetypeMatchupRules {
        ensureLoaded()
        return matchupRules[archetype] ?: ArchetypeMatchupRules(emptyList(), emptyList())
    }

    /**
     * Returns all heroes belonging to [archetype].
     */
    fun getHeroesForArchetype(archetype: String): List<String> {
        ensureLoaded()
        return archetypeHeroes[archetype] ?: emptyList()
    }

    // ── Ally-state gap detection (ported from AlanNobita scoring.py) ──────────

    data class AllyStateGaps(
        val needsMagicDamage: Boolean,
        val frontlineVulnerability: FrontlineVulnerability,
        val presentArchetypes: Set<String>
    )

    enum class FrontlineVulnerability { LOW, MEDIUM, HIGH }

    /**
     * Analyses [allies] (by name) to detect strategic gaps:
     * - [AllyStateGaps.needsMagicDamage]: true if no ally contributes magic damage.
     * - [AllyStateGaps.frontlineVulnerability]: severity of missing frontline.
     *
     * The result is used by [DraftScorer] to award gap-fill bonuses to candidates.
     */
    fun computeAllyStateGaps(allies: List<String>): AllyStateGaps {
        if (allies.isEmpty()) return AllyStateGaps(true, FrontlineVulnerability.MEDIUM, emptySet())

        val allArchetypes = allies.flatMap { getArchetypes(it) }.toSet()
        val hasMagic = allArchetypes.intersect(magicArchetypes).isNotEmpty()
        val hasFrontline = allArchetypes.intersect(protectorArchetypes).isNotEmpty()
        val glassCannonCount = allArchetypes.intersect(glassCannonArchetypes).size

        val vulnerability = when {
            glassCannonCount >= 2 && !hasFrontline -> FrontlineVulnerability.HIGH
            glassCannonCount >= 2 && hasFrontline  -> FrontlineVulnerability.MEDIUM
            else                                   -> FrontlineVulnerability.LOW
        }

        return AllyStateGaps(
            needsMagicDamage = !hasMagic,
            frontlineVulnerability = vulnerability,
            presentArchetypes = allArchetypes
        )
    }

    /**
     * Returns true if [heroName]'s archetypes include at least one magic
     * damage source (Mage sub-role).
     */
    fun isMagicDamageSource(heroName: String): Boolean {
        ensureLoaded()
        return getArchetypes(heroName).any { it in magicArchetypes }
    }

    /**
     * Returns true if [heroName] is a frontline / protector (Tank sub-role).
     */
    fun isFrontlineHero(heroName: String): Boolean {
        ensureLoaded()
        return getArchetypes(heroName).any { it in protectorArchetypes }
    }

    /**
     * Checks whether [candidate] archetype is good_against any archetype
     * present in [enemies] (by name).
     *
     * @return Net archetype matchup score (positive = favourable).
     */
    fun computeArchetypeMatchupScore(
        candidate: String,
        enemies: List<String>
    ): Float {
        ensureLoaded()
        val candidateArchetypes = getArchetypes(candidate)
        if (candidateArchetypes.isEmpty()) return 0f

        var score = 0f
        for (enemy in enemies) {
            val enemyArchetypes = getArchetypes(enemy)
            for (archetype in candidateArchetypes) {
                val rules = getMatchupRules(archetype)
                for (ea in enemyArchetypes) {
                    if (ea in rules.goodAgainst) score += ARCHETYPE_MATCHUP_BONUS
                    if (ea in rules.badAgainst)  score += ARCHETYPE_MATCHUP_PENALTY
                }
            }
        }
        return score
    }

    // ── Archetype redundancy (for team stability bonus) ───────────────────────

    /**
     * Returns the set of archetypes that [candidate] shares with [allies].
     * Shared archetypes incur a redundancy penalty in [DraftScorer].
     */
    fun sharedArchetypes(candidate: String, allies: List<String>): Set<String> {
        ensureLoaded()
        val candidateArch = getArchetypes(candidate).toSet()
        val allyArch = allies.flatMap { getArchetypes(it) }.toSet()
        return candidateArch.intersect(allyArch)
    }

    /**
     * Returns archetypes that [candidate] introduces that no ally has.
     */
    fun uniqueArchetypes(candidate: String, allies: List<String>): Set<String> {
        ensureLoaded()
        val candidateArch = getArchetypes(candidate).toSet()
        val allyArch = allies.flatMap { getArchetypes(it) }.toSet()
        return candidateArch - allyArch
    }

    // ── Loader ────────────────────────────────────────────────────────────────

    private fun ensureLoaded() {
        if (loaded) return
        try {
            val json = context.assets.open("hero_archetypes.json").bufferedReader().readText()
            val root = Json.parseToJsonElement(json).jsonObject
            parseArchetypes(root)
            parseTraits(root)
            parseMatchupRules(root)
            loaded = true
            Timber.d("HeroArchetypeService loaded — ${heroArchetypes.size} heroes, ${archetypeHeroes.size} archetypes")
        } catch (e: Exception) {
            Timber.e(e, "HeroArchetypeService: failed to load hero_archetypes.json — gap detection disabled")
        }
    }

    private fun parseArchetypes(root: JsonObject) {
        val h2a = mutableMapOf<String, MutableList<String>>() // hero → [archetypes]
        val a2h = mutableMapOf<String, MutableList<String>>() // archetype → [heroes]

        for ((key, value) in root) {
            if (key == "traits" || key == "matchup_rules") continue
            // key is archetype name, value is array of hero names
            runCatching {
                val heroes = value.jsonArray.map { it.jsonPrimitive.content }
                a2h.getOrPut(key) { mutableListOf() }.addAll(heroes)
                for (hero in heroes) {
                    h2a.getOrPut(hero) { mutableListOf() }.add(key)
                }
            }.onFailure { Timber.w("HeroArchetypeService: could not parse archetype $key") }
        }

        heroArchetypes = h2a
        archetypeHeroes = a2h
    }

    private fun parseTraits(root: JsonObject) {
        val traitsNode = root["traits"]?.jsonObject ?: return
        val heroToTraits = mutableMapOf<String, MutableSet<String>>()

        for ((traitName, heroArray) in traitsNode) {
            runCatching {
                for (heroEl in heroArray.jsonArray) {
                    val heroName = heroEl.jsonPrimitive.content
                    heroToTraits.getOrPut(heroName) { mutableSetOf() }.add(traitName)
                }
            }.onFailure { Timber.w("HeroArchetypeService: could not parse trait $traitName") }
        }

        heroTraits = heroToTraits
    }

    private fun parseMatchupRules(root: JsonObject) {
        val rulesNode = root["matchup_rules"]?.jsonObject ?: return
        val rules = mutableMapOf<String, ArchetypeMatchupRules>()

        for ((archetype, ruleEl) in rulesNode) {
            runCatching {
                val ruleObj = ruleEl.jsonObject
                val good = ruleObj["good_against"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val bad  = ruleObj["bad_against"]?.jsonArray?.map { it.jsonPrimitive.content }  ?: emptyList()
                rules[archetype] = ArchetypeMatchupRules(good, bad)
            }.onFailure { Timber.w("HeroArchetypeService: could not parse matchup rules for $archetype") }
        }

        matchupRules = rules
    }

    companion object {
        private const val ARCHETYPE_MATCHUP_BONUS   =  0.05f
        private const val ARCHETYPE_MATCHUP_PENALTY = -0.05f
    }
}
