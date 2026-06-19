package com.mlbb.assistant.domain.repository

import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.flow.Flow

/**
 * Pure-domain contract for accessing hero meta data.
 *
 * The concrete implementation ([data.repository.HeroRepositoryImpl]) is wired
 * via Hilt in [di.RepositoryModule] so the domain layer stays free of Android
 * and data-layer dependencies.
 */
interface HeroRepository {
    /** Live stream of all heroes ordered by tier rank then win rate. */
    fun getHeroes(): Flow<List<Hero>>

    /** Live stream of heroes whose name contains [query]. */
    fun searchHeroes(query: String): Flow<List<Hero>>

    /** Returns a single hero by [id], or null if not found. */
    suspend fun getHeroById(id: Int): Hero?

    /** Batch-fetch heroes by a list of [ids]. Order is not guaranteed. */
    suspend fun getHeroesByIds(ids: List<Int>): List<Hero>

    /**
     * Returns up to [limit] top-meta heroes ordered by tier rank then win rate.
     * Used for the home screen spotlight and overlay quick-summary.
     */
    suspend fun getTopMetaHeroes(limit: Int = 5): List<Hero>

    /**
     * Fetches the latest meta snapshot from the remote API and upserts it
     * into the local database. Falls back to a bundled JSON seed if the
     * network is unavailable and the database is empty.
     */
    suspend fun syncHeroes()
}
