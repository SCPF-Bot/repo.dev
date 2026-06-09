package com.mlbb.assistant.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HeroDao {
    @Query("SELECT * FROM heroes")
    fun getAllHeroes(): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE id = :heroId")
    suspend fun getHeroById(heroId: Int): HeroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(heroes: List<HeroEntity>)

    @Query("DELETE FROM heroes")
    suspend fun deleteAll()
}