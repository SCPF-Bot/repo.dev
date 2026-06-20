package com.mlbb.assistant.domain.advisor

import com.mlbb.assistant.domain.model.Hero

/**
 * Infers the enemy team's probable composition archetype from partial
 * information (picks seen so far + banned heroes).  Exposed as a simple
 * object so it can be called inside OverlayService without DI overhead.
 *
 * The result is a nullable [CompositionArchetype] — null means we have
 * seen too few enemy picks to make a meaningful inference (< 2 picks).
 */
object EnemyIntentAnalyzer {

    private const val MIN_PICKS_FOR_INFERENCE = 2

    /**
     * Analyses [enemyPicks] and returns the most likely archetype together
     * with a human-readable intent string (shown in the overlay).
     */
    fun infer(enemyPicks: List<Hero>): EnemyIntent? {
        if (enemyPicks.size < MIN_PICKS_FOR_INFERENCE) return null

        val profile  = CompositionAnalyzer.analyze(enemyPicks)
        val roles    = enemyPicks.map { it.role }
        val ccUltCnt = enemyPicks.count { it.hasCCUlt }
        val archetype = CompositionArchetype.detect(profile, roles, ccUltCnt)

        val intent = buildIntentString(archetype, enemyPicks)
        val counterAdvice = archetype.counterCondition

        return EnemyIntent(archetype, intent, counterAdvice)
    }

    private fun buildIntentString(archetype: CompositionArchetype, picks: List<Hero>): String {
        val names = picks.take(2).joinToString(" + ") { it.name }
        return when (archetype) {
            CompositionArchetype.DIVE        -> "Enemy is building a DIVE comp ($names...) — expect early engage"
            CompositionArchetype.POKE        -> "Enemy is building a POKE comp ($names...) — expect range harassment"
            CompositionArchetype.TURTLE      -> "Enemy is building a TURTLE comp ($names...) — expect objective control"
            CompositionArchetype.WOMBO_COMBO -> "Enemy is building a COMBO comp ($names...) — do NOT clump together"
            CompositionArchetype.SPLIT_PUSH  -> "Enemy is building a SPLIT comp ($names...) — expect dual-lane pressure"
            CompositionArchetype.BALANCED    -> "Enemy composition looks balanced ($names...)"
        }
    }
}

/**
 * Result of an enemy intent analysis pass.
 *
 * @param archetype       The inferred [CompositionArchetype].
 * @param intentSummary   One-sentence human-readable description of the threat.
 * @param counterAdvice   Actionable counter-play advice for the overlay.
 */
data class EnemyIntent(
    val archetype: CompositionArchetype,
    val intentSummary: String,
    val counterAdvice: String
)
