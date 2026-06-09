package com.mlbb.assistant.data.repository

import android.content.Context
import com.mlbb.assistant.data.local.database.HeroDao
import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.data.remote.api.MetaApi
import com.mlbb.assistant.data.remote.dto.HeroDto
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.utils.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaApi: MetaApi,
    private val context: Context
) : HeroRepository {

    override fun getAllHeroes(): Flow<List<Hero>> {
        return heroDao.getAllHeroes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun syncHeroes(): Result<Unit> {
        return try {
            val response = metaApi.getMetaSnapshot()
            val heroes = response.heroes.map { it.toEntity() }
            heroDao.insertAll(heroes)
            Result.success(Unit)
        } catch (e: Exception) {
            // If network fails, try to load from assets if DB is empty
            if (heroDao.getAllHeroes().first().isEmpty()) {
                val fallbackHeroes = JsonParser.loadHeroesFromAssets(context)
                if (fallbackHeroes.isNotEmpty()) {
                    heroDao.insertAll(fallbackHeroes.map { it.toEntity() })
                    return Result.success(Unit)
                }
            }
            Result.failure(e)
        }
    }

    override suspend fun getHeroById(id: Int): Hero? {
        return heroDao.getHeroById(id)?.toDomain()
    }

    private fun HeroDto.toEntity() = HeroEntity(
        id = id,
        name = name,
        role = role,
        winRate = winRate,
        pickRate = pickRate,
        banRate = banRate,
        imageUrl = imageUrl,
        counters = counters,
        synergies = synergies
    )

    private fun HeroEntity.toDomain() = Hero(
        id = id,
        name = name,
        role = role,
        winRate = winRate,
        pickRate = pickRate,
        banRate = banRate,
        imageUrl = imageUrl,
        counters = counters,
        synergies = synergies
    )
}