package com.mlbbassistant.data.repository

import com.mlbbassistant.core.Resource
import com.mlbbassistant.core.safeCall
import com.mlbbassistant.data.api.MlbbApiService
import com.mlbbassistant.data.api.dto.toEntity
import com.mlbbassistant.data.db.dao.HeroDao
import com.mlbbassistant.data.db.dao.MetaSnapshotDao
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaSnapshotDao: MetaSnapshotDao,
    private val apiService: MlbbApiService
) : HeroRepository {

    override fun observeHeroes(): Flow<List<Hero>> =
        heroDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByRole(role: HeroRole): Flow<List<Hero>> =
        heroDao.observeByRole(role.name).map { list -> list.map { it.toDomain() } }

    override fun searchHeroes(query: String): Flow<List<Hero>> =
        heroDao.search(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getHeroById(id: Int): Hero? =
        heroDao.getById(id)?.toDomain()

    override suspend fun refreshHeroes(patch: String?): Resource<Unit> = safeCall {
        val snapshot = apiService.getMetaSnapshot(patch)
        heroDao.upsertAll(snapshot.heroes.map { it.toEntity() })
        metaSnapshotDao.upsert(snapshot.toEntity())
    }

    override suspend fun getLastSyncedPatch(): String? =
        metaSnapshotDao.getLatest()?.patch
}
