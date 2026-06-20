package com.mlbb.assistant.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the personal hero pool.
 *
 * Uses [OnConflictStrategy.REPLACE] for upsert so a single call handles
 * both adding a new hero and updating proficiency for an existing entry.
 */
@Dao
interface HeroPoolDao {

    /** Observe the full pool as a live stream.  Emits on every change. */
    @Query("SELECT * FROM hero_pool")
    fun getAll(): Flow<List<HeroPoolEntity>>

    /** Snapshot read — useful when a single value is needed synchronously. */
    @Query("SELECT * FROM hero_pool WHERE heroId = :heroId")
    suspend fun getById(heroId: Int): HeroPoolEntity?

    /** Add or update a pool entry (upsert). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HeroPoolEntity)

    /** Remove a hero from the pool. */
    @Query("DELETE FROM hero_pool WHERE heroId = :heroId")
    suspend fun delete(heroId: Int)

    /** Clear the entire pool. */
    @Query("DELETE FROM hero_pool")
    suspend fun clearAll()
}
