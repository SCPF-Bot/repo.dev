package com.mlbb.assistant.domain.repository

import com.mlbb.assistant.domain.model.DraftHistoryItem
import kotlinx.coroutines.flow.Flow

/**
 * Pure-domain contract for persisting and querying completed draft sessions.
 *
 * Previously, [data.local.database.DraftSessionEntity] and
 * [data.local.database.DraftSessionDao] existed but were never called —
 * sessions were computed and immediately discarded. This interface wires
 * the domain to the data layer so sessions are now persisted and surfaced
 * in the History screen.
 *
 * The concrete implementation ([data.repository.DraftSessionRepositoryImpl])
 * is injected by Hilt via [di.RepositoryModule].
 */
interface DraftSessionRepository {

    /** Live stream of all sessions, newest first. */
    fun getAllSessions(): Flow<List<DraftHistoryItem>>

    /** Live stream of the most recent [limit] sessions. */
    fun getRecentSessions(limit: Int = 20): Flow<List<DraftHistoryItem>>

    /**
     * Persists a draft history item.
     * @return The auto-generated row ID of the inserted record.
     */
    suspend fun saveSession(item: DraftHistoryItem): Long

    /** Deletes a single session by its [id]. */
    suspend fun deleteSession(id: Int)

    /** Deletes all sessions (used for "Clear History" in Settings). */
    suspend fun deleteAllSessions()
}
