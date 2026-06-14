package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.data.repository.HeroRepository
import javax.inject.Inject

class GetHeroesUseCase @Inject constructor(private val repository: HeroRepository) {
    operator fun invoke() = repository.getHeroes()
}
