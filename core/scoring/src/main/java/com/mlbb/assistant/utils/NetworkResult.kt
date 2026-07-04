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
 * Transforms the [NetworkResult.Success.data] value with [transform], leaving
 * [Loading] and [Error] states unchanged.  Useful for chaining data conversions
 * in repositories without unwrapping the result.
 *
 * Example:
 *   heroResult.mapSuccess { entities -> entities.map { it.toDomain() } }
 */
inline fun <T, R> NetworkResult<T>.mapSuccess(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Loading -> NetworkResult.Loading
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Error   -> this
}

/**
 * Runs [action] when this is [NetworkResult.Success], then returns the original result
 * unchanged.  Designed for side-effects such as caching or logging inside a chain.
 */
inline fun <T> NetworkResult<T>.onSuccess(action: (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) action(data)
    return this
}

/**
 * Runs [action] when this is [NetworkResult.Error], then returns the original result
 * unchanged.  Designed for side-effects such as error reporting or analytics.
 */
inline fun <T> NetworkResult<T>.onError(action: (message: String, cause: Throwable?) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Error) action(message, cause)
    return this
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
