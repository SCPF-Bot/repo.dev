package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.repository.HeroRepository
import com.mlbb.assistant.utils.NetworkResult
import javax.inject.Inject

/**
 * Use case: trigger a meta snapshot sync from the remote API.
 *
 * Wraps the repository call in [NetworkResult] so the ViewModel can
 * distinguish loading, success, and error states without catching
 * exceptions directly.
 */
class SyncHeroesUseCase @Inject constructor(
    private val repository: HeroRepository
) {
    suspend operator fun invoke(): NetworkResult<Unit> =
        runCatching { repository.syncHeroes() }
            .fold(
                onSuccess = { NetworkResult.Success(Unit) },
                onFailure = { e -> NetworkResult.Error(e.message ?: "Sync failed", e) }
            )
}
