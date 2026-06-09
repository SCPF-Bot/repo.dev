// File: app/src/main/java/com/mlbb/assistant/domain/usecase/GetHeroesUseCase.kt
package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.data.repository.HeroRepository
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHeroesUseCase @Inject constructor(
    private val repository: HeroRepository
) {
    operator fun invoke(): Flow<List<Hero>> = repository.getAllHeroes()
}