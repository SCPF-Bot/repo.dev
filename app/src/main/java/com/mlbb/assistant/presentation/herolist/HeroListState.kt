package com.mlbb.assistant.presentation.herolist

import com.mlbb.assistant.domain.model.Hero

data class HeroListState(
    val heroes: List<Hero> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)