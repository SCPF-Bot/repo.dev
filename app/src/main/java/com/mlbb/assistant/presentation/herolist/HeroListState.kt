package com.mlbb.assistant.presentation.herolist

import androidx.compose.runtime.Immutable
import com.mlbb.assistant.domain.model.Hero

@Immutable
data class HeroListState(
    val heroes: List<Hero>         = emptyList(),
    val filteredHeroes: List<Hero> = emptyList(),
    val searchQuery: String        = "",
    val selectedRole: String?      = null,
    val isLoading: Boolean         = false,
    val error: String?             = null
)
