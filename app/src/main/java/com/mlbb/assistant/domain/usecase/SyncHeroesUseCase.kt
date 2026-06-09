// File: app/src/main/java/com/mlbb/assistant/domain/usecase/SyncHeroesUseCase.kt
package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.data.repository.HeroRepository
import javax.inject.Inject

class SyncHeroesUseCase @Inject constructor(
    private val repository: HeroRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.syncHeroes()
}