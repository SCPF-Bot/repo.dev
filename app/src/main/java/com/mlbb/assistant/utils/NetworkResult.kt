package com.mlbb.assistant.utils

/**
 * Standard sealed resource wrapper for network / async operations.
 *
 * Usage in repositories:
 *   emit(NetworkResult.Loading)
 *   emit(NetworkResult.Success(data))
 *   emit(NetworkResult.Error(message, cause))
 *
 * Usage in ViewModels (when-expression):
 *   when (result) {
 *     is NetworkResult.Loading -> _state.update { it.copy(isLoading = true) }
 *     is NetworkResult.Success -> _state.update { it.copy(data = result.data, isLoading = false) }
 *     is NetworkResult.Error   -> _state.update { it.copy(error = result.message, isLoading = false) }
 *   }
 *
 * Usage with [fold] (functional style — avoids unchecked smart casts):
 *   result.fold(
 *     onLoading = { ... },
 *     onSuccess = { data -> ... },
 *     onError   = { msg, cause -> ... }
 *   )
 */
sealed class NetworkResult<out T> {
    data object Loading : NetworkResult<Nothing>()
    data class  Success<T>(val data: T) : NetworkResult<T>()
    data class  Error(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>()
}

/**
 * Functional fold over all three states of [NetworkResult].
 *
 * Preferred over a raw `when` expression when the caller needs to return a value,
 * because it exhausts all branches at the call site without requiring an `else`.
 */
inline fun <T, R> NetworkResult<T>.fold(
    onLoading: () -> R,
    onSuccess: (T) -> R,
    onError:   (message: String, cause: Throwable?) -> R
): R = when (this) {
    is NetworkResult.Loading -> onLoading()
    is NetworkResult.Success -> onSuccess(data)
    is NetworkResult.Error   -> onError(message, cause)
}

/**
 * Returns the [NetworkResult.Success.data] value, or `null` for [Loading] / [Error].
 * Useful for one-liner null checks in ViewModels.
 */
fun <T> NetworkResult<T>.getOrNull(): T? =
    if (this is NetworkResult.Success) data else null

/**
 * Convenience builder: wraps a suspending block in a [NetworkResult].
 * Returns [NetworkResult.Success] on completion or [NetworkResult.Error] on any exception.
 */
suspend fun <T> networkResultOf(block: suspend () -> T): NetworkResult<T> = runCatching {
    NetworkResult.Success(block())
}.getOrElse { ex ->
    NetworkResult.Error(ex.message ?: "Unknown error", ex)
}
