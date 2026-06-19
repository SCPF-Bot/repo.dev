package com.mlbb.assistant.domain.repository

import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for hero data.
 *
 * Returns [Hero] domain objects; the data layer owns the mapping from
 * [com.mlbb.assistant.data.local.database.HeroEntity] to [Hero].
 * Domain use cases depend only on this interface, never on the data layer directly.
 */
interface HeroRepository {
    fun getHeroes(): Flow<List<Hero>>
    fun searchHeroes(query: String): Flow<List<Hero>>
    suspend fun syncHeroes()
    suspend fun getHeroById(id: Int): Hero?
    suspend fun getHeroesByIds(ids: List<Int>): List<Hero>
    suspend fun getTopMetaHeroes(limit: Int = 5): List<Hero>
}
