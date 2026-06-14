package com.mlbb.assistant.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HeroDao {

    @Query("SELECT * FROM heroes ORDER BY tier ASC, winRate DESC")
    abstract fun getAllHeroes(): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE role = :role ORDER BY tier ASC, winRate DESC")
    abstract fun getHeroesByRole(role: String): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE lane = :lane ORDER BY tier ASC, winRate DESC")
    abstract fun getHeroesByLane(lane: String): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE id = :heroId")
    abstract suspend fun getHeroById(heroId: Int): HeroEntity?

    @Query("SELECT * FROM heroes WHERE id IN (:ids)")
    abstract suspend fun getHeroesByIds(ids: List<Int>): List<HeroEntity>

    @Query("SELECT * FROM heroes WHERE name LIKE '%' || :query || '%' ORDER BY tier ASC, winRate DESC")
    abstract fun searchHeroes(query: String): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE isOP = 1 OR isToxicMechanic = 1 ORDER BY banRate DESC LIMIT 10")
    abstract suspend fun getHighPriorityBans(): List<HeroEntity>

    @Query("SELECT * FROM heroes ORDER BY winRate DESC, tier ASC LIMIT :limit")
    abstract suspend fun getTopMetaHeroes(limit: Int = 5): List<HeroEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(heroes: List<HeroEntity>)

    @Query("DELETE FROM heroes")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replaceAll(heroes: List<HeroEntity>) {
        deleteAll()
        insertAll(heroes)
    }
}
