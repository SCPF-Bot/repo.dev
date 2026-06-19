package com.mlbb.assistant.data.repository

import com.mlbb.assistant.data.local.database.HeroDao
import com.mlbb.assistant.data.remote.api.MetaApi
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import com.mlbb.assistant.utils.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaApi: MetaApi,
    private val jsonParser: JsonParser
) : HeroRepository {

    override fun getHeroes(): Flow<List<Hero>> =
        heroDao.getAllHeroes().map { entities -> entities.map { it.toDomain() } }

    override fun searchHeroes(query: String): Flow<List<Hero>> =
        heroDao.searchHeroes(query).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getHeroById(id: Int): Hero? =
        heroDao.getHeroById(id)?.toDomain()

    override suspend fun getHeroesByIds(ids: List<Int>): List<Hero> =
        heroDao.getHeroesByIds(ids).map { it.toDomain() }

    override suspend fun getTopMetaHeroes(limit: Int): List<Hero> =
        heroDao.getTopMetaHeroes(limit).map { it.toDomain() }

    override suspend fun syncHeroes() {
        runCatching {
            Timber.d("syncHeroes: fetching meta snapshot from network")
            val snapshot = metaApi.getMetaSnapshot()
            heroDao.replaceAll(snapshot.heroes.map { it.toEntity() })
            Timber.i("syncHeroes: synced ${snapshot.heroes.size} heroes from network")
        }.onFailure { error ->
            Timber.w(error, "syncHeroes: network failed — falling back to local JSON seed")
            val existing = heroDao.getTopMetaHeroes(1)
            if (existing.isEmpty()) {
                Timber.d("syncHeroes: DB empty, seeding from bundled JSON")
                seedFromJson()
            }
        }
    }

    private suspend fun seedFromJson() {
        val heroes = jsonParser.parseHeroes()
        if (heroes.isNotEmpty()) {
            heroDao.replaceAll(heroes)
            Timber.i("seedFromJson: inserted ${heroes.size} heroes from bundled JSON")
        } else {
            Timber.w("seedFromJson: bundled JSON parse returned empty list")
        }
    }
}
