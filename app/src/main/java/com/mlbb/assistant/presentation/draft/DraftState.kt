// File: app/src/main/java/com/mlbb/assistant/presentation/draft/DraftState.kt
package com.mlbb.assistant.presentation.draft

import com.mlbb.assistant.domain.model.Hero

data class DraftState(
    val allies: List<Hero> = emptyList(),
    val enemies: List<Hero> = emptyList(),
    val bans: List<Hero> = emptyList(),
    val suggestions: List<Pair<Hero, Double>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)