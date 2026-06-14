package com.mlbb.assistant.data.repository

import com.mlbb.assistant.data.local.database.HeroEntity
import kotlinx.coroutines.flow.Flow

interface HeroRepository {
    fun getHeroes(): Flow<List<HeroEntity>>
    fun searchHeroes(query: String): Flow<List<HeroEntity>>
    suspend fun syncHeroes()
    suspend fun getHeroById(id: Int): HeroEntity?
    suspend fun getHeroesByIds(ids: List<Int>): List<HeroEntity>
    suspend fun getTopMetaHeroes(limit: Int = 5): List<HeroEntity>
}
