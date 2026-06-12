package com.mlbb.assistant.data.repository

import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.flow.Flow

interface HeroRepository {
    fun getAllHeroes(): Flow<List<Hero>>
    suspend fun syncHeroes(): Result<Unit>
    suspend fun getHeroById(id: Int): Hero?
}