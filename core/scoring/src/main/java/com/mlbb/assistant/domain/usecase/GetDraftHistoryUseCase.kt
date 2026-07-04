package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.repository.DraftSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves saved draft sessions from the local Room database.
 *
 * Returns a [Flow] that emits whenever the underlying table changes,
 * giving the history screen automatic live updates without polling.
 *
 * @param limit Maximum number of sessions to return (most recent first).
 *              Pass [Int.MAX_VALUE] or omit to retrieve all sessions.
 */
class GetDraftHistoryUseCase @Inject constructor(
    private val repository: DraftSessionRepository
) {

    /**
     * Returns the [limit] most recent draft sessions, ordered newest-first.
     * Defaults to the last 20 sessions — enough for a scrollable history
     * screen without loading the entire database into memory.
     */
    operator fun invoke(limit: Int = 20): Flow<List<DraftHistoryItem>> =
        repository.getRecentSessions(limit)

    /** Returns all sessions with no limit. Use with Paging 3 for large histories. */
    fun all(): Flow<List<DraftHistoryItem>> =
        repository.getAllSessions()
}
