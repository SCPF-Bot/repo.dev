package com.mlbb.assistant.utils

/**
 * Standard sealed resource wrapper for network / async operations.
 *
 * Usage in repositories:
 *   emit(NetworkResult.Loading)
 *   emit(NetworkResult.Success(data))
 *   emit(NetworkResult.Error(message, cause))
 *
 * Usage in ViewModels:
 *   when (result) {
 *     is NetworkResult.Loading -> _state.update { it.copy(isLoading = true) }
 *     is NetworkResult.Success -> _state.update { it.copy(data = result.data, isLoading = false) }
 *     is NetworkResult.Error   -> _state.update { it.copy(error = result.message, isLoading = false) }
 *   }
 */
sealed class NetworkResult<out T> {
    data object Loading : NetworkResult<Nothing>()
    data class  Success<T>(val data: T) : NetworkResult<T>()
    data class  Error(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>()
}

/**
 * Convenience builder: wraps a suspending block in a [NetworkResult].
 * Returns [NetworkResult.Success] on completion or [NetworkResult.Error] on any exception.
 */
suspend fun <T> networkResultOf(block: suspend () -> T): NetworkResult<T> = runCatching {
    NetworkResult.Success(block())
}.getOrElse { ex ->
    NetworkResult.Error(ex.message ?: "Unknown error", ex)
}
