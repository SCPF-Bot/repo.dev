package com.mlbb.assistant.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HeroDao {

    @Query("SELECT * FROM heroes")
    abstract fun getAllHeroes(): Flow<List<HeroEntity>>

    @Query("SELECT * FROM heroes WHERE id = :heroId")
    abstract suspend fun getHeroById(heroId: Int): HeroEntity?

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
