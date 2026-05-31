package com.mlbbassistant.data.db.dao

import androidx.room.*
import com.mlbbassistant.data.db.entity.HeroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeroDao {

    @Query("SELECT * FROM heroes ORDER BY name ASC")
    fun observeAll(): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes ORDER BY name ASC")
    suspend fun getAll(): List<HeroEntity>

    @Query("SELECT * FROM heroes WHERE id = :heroId")
    suspend fun getById(heroId: Int): HeroEntity?

    @Query("""
        SELECT * FROM heroes
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE role = :role ORDER BY win_rate DESC")
    fun observeByRole(role: String): Flow<List<HeroEntity>>

    @Upsert
    suspend fun upsertAll(heroes: List<HeroEntity>)

    @Query("DELETE FROM heroes")
    suspend fun deleteAll()
}
