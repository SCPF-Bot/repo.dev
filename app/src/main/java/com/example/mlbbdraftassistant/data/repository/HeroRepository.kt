package com.example.mlbbdraftassistant.data.repository

import com.example.mlbbdraftassistant.data.model.Hero
import kotlinx.coroutines.flow.Flow

interface HeroRepository {
    fun observeHeroes(): Flow<List<Hero>>
    suspend fun refreshHeroData(): Result<Unit>
    suspend fun getHeroById(id: Int): Hero?
}