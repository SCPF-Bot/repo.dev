package com.mlbbassistant.data.repository

import com.mlbbassistant.core.Resource
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroRole
import kotlinx.coroutines.flow.Flow

interface HeroRepository {

    /** Emits the full hero roster from the local DB, updating whenever the DB changes. */
    fun observeHeroes(): Flow<List<Hero>>

    /** Emits heroes filtered by [role], ordered by win rate descending. */
    fun observeByRole(role: HeroRole): Flow<List<Hero>>

    /** Live search of hero names. */
    fun searchHeroes(query: String): Flow<List<Hero>>

    /** Fetches the hero [id] from cache; returns null if not found. */
    suspend fun getHeroById(id: Int): Hero?

    /**
     * Refreshes hero data from the remote API and persists it to the DB.
     * Returns [Resource.Success] on success, [Resource.Error] on failure.
     * The [patch] parameter is optional; null = latest patch.
     */
    suspend fun refreshHeroes(patch: String? = null): Resource<Unit>

    /** Returns the patch string stored in the last successful sync, or null. */
    suspend fun getLastSyncedPatch(): String?
}
