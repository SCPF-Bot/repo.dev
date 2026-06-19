package com.mlbb.assistant.utils

/**
 * Standard resource wrapper for network / async operations.
 *
 * Replaces ad-hoc [Result] + nullable patterns scattered across ViewModels
 * with a single, typed sealed hierarchy that makes loading/success/error
 * states explicit in both the ViewModel and the UI.
 *
 * Usage in a ViewModel:
 * ```kotlin
 * when (val result = syncHeroesUseCase()) {
 *     is NetworkResult.Loading -> _uiState.update { it.copy(isLoading = true) }
 *     is NetworkResult.Success -> _uiState.update { it.copy(isLoading = false) }
 *     is NetworkResult.Error   -> _uiState.update { it.copy(error = result.message) }
 * }
 * ```
 */
sealed class NetworkResult<out T> {

    /** The operation completed successfully and returned [data]. */
    data class Success<T>(val data: T) : NetworkResult<T>()

    /**
     * The operation failed.
     * @param message Human-readable error message safe to display in the UI.
     * @param cause   Original throwable for logging (not shown to users).
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : NetworkResult<Nothing>()

    /** The operation is in progress. */
    data object Loading : NetworkResult<Nothing>()

    // ── Convenience extensions ─────────────────────────────────────────────

    val isSuccess: Boolean get() = this is Success
    val isError:   Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
}
