package com.mlbb.assistant.domain.repository

import com.mlbb.assistant.domain.model.DraftHistoryItem
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for persisting and querying completed draft sessions.
 *
 * The data layer owns the mapping between [com.mlbb.assistant.data.local.database.DraftSessionEntity]
 * and [DraftHistoryItem]. Domain use cases depend only on this interface.
 */
interface DraftSessionRepository {

    /** Observe all saved sessions, newest first. */
    fun getAllSessions(): Flow<List<DraftHistoryItem>>

    /** Observe the [limit] most recent sessions. */
    fun getRecentSessions(limit: Int = 20): Flow<List<DraftHistoryItem>>

    /** Persist a completed draft session. Returns the row id of the inserted record. */
    suspend fun saveSession(item: DraftHistoryItem): Long

    /** Delete a single session by its [id]. */
    suspend fun deleteSession(id: Int)

    /** Wipe all saved draft sessions. */
    suspend fun deleteAllSessions()
}
