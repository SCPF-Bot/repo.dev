package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.repository.HeroRepository
import javax.inject.Inject

class SyncHeroesUseCase @Inject constructor(private val repository: HeroRepository) {
    suspend operator fun invoke() = repository.syncHeroes()
}
