package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: observe the live list of all heroes ordered by tier rank.
 *
 * Thin delegation to [HeroRepository.getHeroes]; exists as a use case so
 * ViewModels depend on the domain layer, not the data layer directly.
 */
class GetHeroesUseCase @Inject constructor(
    private val repository: HeroRepository
) {
    operator fun invoke(): Flow<List<Hero>> = repository.getHeroes()
}
