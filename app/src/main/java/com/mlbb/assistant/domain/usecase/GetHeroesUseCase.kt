package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHeroesUseCase @Inject constructor(private val repository: HeroRepository) {
    operator fun invoke(): Flow<List<Hero>> = repository.getHeroes()
}
