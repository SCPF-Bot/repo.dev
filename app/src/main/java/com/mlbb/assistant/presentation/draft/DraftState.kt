package com.mlbb.assistant.presentation.draft

import androidx.compose.runtime.Immutable
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.HeroScore

/**
 * Immutable UI state for the Draft screen / ViewModel.
 *
 * [suggestions] now carries [HeroScore] objects instead of [Pair<Hero, Double>] so that
 * the UI can display the badge label and reasoning produced by the scoring engine — without
 * any information being discarded at the ViewModel boundary.
 */
@Immutable
data class DraftState(
    val allies:      List<Hero>      = emptyList(),
    val enemies:     List<Hero>      = emptyList(),
    val bans:        List<Hero>      = emptyList(),
    val suggestions: List<HeroScore> = emptyList(),
    val isLoading:   Boolean         = false,
    val error:       String?         = null,
    val sessionSaved: Boolean        = false
)
