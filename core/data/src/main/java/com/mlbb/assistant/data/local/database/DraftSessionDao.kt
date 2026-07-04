package com.mlbb.assistant.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DraftSessionEntity): Long

    @Query("SELECT * FROM draft_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<DraftSessionEntity>>

    @Query("SELECT * FROM draft_sessions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 20): Flow<List<DraftSessionEntity>>

    @Query("SELECT * FROM draft_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): DraftSessionEntity?

    @Query("DELETE FROM draft_sessions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM draft_sessions")
    suspend fun deleteAll()
}
