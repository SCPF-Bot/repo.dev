package com.mlbb.assistant.data.repository

import android.content.Context
import com.mlbb.assistant.data.local.database.HeroDao
import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.data.remote.api.MetaApi
import com.mlbb.assistant.data.remote.dto.HeroDto
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.utils.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaApi: MetaApi,
    @ApplicationContext private val context: Context
) : HeroRepository {

    override fun getAllHeroes(): Flow<List<Hero>> =
        heroDao.getAllHeroes().map { entities -> entities.map { it.toDomain() } }

    override suspend fun syncHeroes(): Result<Unit> {
        return try {
            val response = metaApi.getMetaSnapshot()
            // Atomic replace: delete stale heroes + insert fresh data in one transaction
            heroDao.replaceAll(response.heroes.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            // Only attempt fallback when DB is truly empty — avoid redundant query otherwise
            val isEmpty = try {
                withContext(Dispatchers.IO) { heroDao.getAllHeroes().first().isEmpty() }
            } catch (_: Exception) { true }
            if (isEmpty) {
                try {
                    val fallback = JsonParser.loadHeroesFromAssets(context)
                    if (fallback.isNotEmpty()) {
                        heroDao.insertAll(fallback.map { it.toEntity() })
                        return Result.success(Unit)
                    }
                } catch (_: Exception) { }
            }
            Result.failure(e)
        }
    }

    override suspend fun getHeroById(id: Int): Hero? =
        heroDao.getHeroById(id)?.toDomain()

    private fun HeroDto.toEntity() = HeroEntity(
        id = id, name = name, role = role,
        winRate = winRate, pickRate = pickRate, banRate = banRate,
        imageUrl = imageUrl, counters = counters, synergies = synergies
    )

    private fun HeroEntity.toDomain() = Hero(
        id = id, name = name, role = role,
        winRate = winRate, pickRate = pickRate, banRate = banRate,
        imageUrl = imageUrl, counters = counters, synergies = synergies
    )
}
