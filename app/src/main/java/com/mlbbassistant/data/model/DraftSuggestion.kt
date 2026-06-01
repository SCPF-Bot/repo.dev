package com.mlbbassistant.data.model

/**
 * A ranked hero recommendation produced by the suggestion engine.
 *
 * @param hero        The suggested hero.
 * @param score       Composite score in [0, 1] used for ranking.
 * @param reason      Human-readable explanation for the suggestion.
 * @param counterScore Contribution from counter-pick advantage.
 * @param synergyScore Contribution from team synergy.
 * @param metaScore    Contribution from win/ban/pick rate meta weight.
 */
data class DraftSuggestion(
    val hero: Hero,
    val score: Float,
    val reason: String,
    val counterScore: Float = 0f,
    val synergyScore: Float = 0f,
    val metaScore: Float    = 0f
)
