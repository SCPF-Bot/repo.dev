package com.mlbb.assistant.data.repository

import com.mlbb.assistant.data.local.database.HeroDao
import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.data.remote.api.MetaApi
import com.mlbb.assistant.utils.JsonParser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaApi: MetaApi,
    private val jsonParser: JsonParser
) : HeroRepository {

    override fun getHeroes(): Flow<List<HeroEntity>> = heroDao.getAllHeroes()

    override fun searchHeroes(query: String): Flow<List<HeroEntity>> =
        heroDao.searchHeroes(query)

    override suspend fun getHeroById(id: Int): HeroEntity? = heroDao.getHeroById(id)

    override suspend fun getHeroesByIds(ids: List<Int>): List<HeroEntity> =
        heroDao.getHeroesByIds(ids)

    override suspend fun getTopMetaHeroes(limit: Int): List<HeroEntity> =
        heroDao.getTopMetaHeroes(limit)

    override suspend fun syncHeroes() {
        runCatching {
            val snapshot = metaApi.getMetaSnapshot()
            heroDao.replaceAll(snapshot.heroes.map { it.toEntity() })
        }.onFailure {
            // network failure — seed from local JSON if DB is empty
            val existing = heroDao.getTopMetaHeroes(1)
            if (existing.isEmpty()) seedFromJson()
        }
    }

    private suspend fun seedFromJson() {
        val heroes = jsonParser.parseHeroes()
        if (heroes.isNotEmpty()) heroDao.replaceAll(heroes)
    }
}
