package com.mlbb.assistant.presentation.herolist

import com.mlbb.assistant.domain.model.Hero

data class HeroListState(
    val heroes: List<Hero> = emptyList(),
    val filteredHeroes: List<Hero> = emptyList(),
    val searchQuery: String = "",
    val selectedRole: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
