package com.mlbb.assistant.domain.repository

import androidx.paging.PagingData
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

    /**
     * TD-10: Returns a [PagingData] stream for the hero grid.
     * Callers set the page size; the repository owns the [Pager] construction.
     *
     * @param query    Name filter (empty = all heroes).
     * @param lane     Lane filter (empty = all lanes).
     * @param pageSize Number of heroes per page (default 30).
     */
    fun getHeroesPaged(
        query:    String = "",
        lane:     String = "",
        pageSize: Int    = 30
    ): Flow<PagingData<Hero>>
}
