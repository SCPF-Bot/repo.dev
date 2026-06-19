package com.mlbb.assistant.domain.model

import androidx.compose.runtime.Stable

/**
 * Scored hero recommendation produced by the draft advisor.
 *
 * Replaces the previous [Pair<Hero, Double>] representation which discarded
 * the badge label and per-dimension scores, making it impossible to render
 * breakdown information in the UI.
 *
 * Annotated [@Stable] so Compose recomposition is skipped when the instance
 * has not changed (all properties are immutable [val]).
 */
@Stable
data class HeroScore(
    val hero: Hero,
    val totalScore: Double,
    val metaScore: Double,
    val counterScore: Double,
    val synergyScore: Double,
    /** Short badge displayed on the suggestion card: "OP Meta", "Counter", "Synergy", etc. */
    val badgeLabel: String,
    /** One-sentence rationale shown beneath the hero name. */
    val reason: String
)
