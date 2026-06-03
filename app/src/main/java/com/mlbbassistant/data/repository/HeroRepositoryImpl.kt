package com.mlbbassistant.data.repository

import android.util.Log
import com.google.gson.Gson
import com.mlbbassistant.core.Resource
import com.mlbbassistant.data.api.dto.toEntity
import com.mlbbassistant.data.db.dao.HeroDao
import com.mlbbassistant.data.db.dao.MetaSnapshotDao
import com.mlbbassistant.data.db.entity.HeroEntity
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroRole
import com.mlbbassistant.di.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaSnapshotDao: MetaSnapshotDao,
    private val assetDataSource: AssetHeroDataSource,
    private val userPreferences: UserPreferences,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : HeroRepository {

    companion object {
        private const val TAG = "HeroRepository"
    }

    override fun observeHeroes(): Flow<List<Hero>> =
        heroDao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            .catch { e ->
                Log.e(TAG, "observeHeroes DB error", e)
                emit(emptyList())
            }

    override fun observeByRole(role: HeroRole): Flow<List<Hero>> =
        heroDao.observeByRole(role.name)
            .map { list -> list.map { it.toDomain() } }
            .catch { e ->
                Log.e(TAG, "observeByRole DB error", e)
                emit(emptyList())
            }

    override fun searchHeroes(query: String): Flow<List<Hero>> =
        heroDao.search(query.trim())
            .map { list -> list.map { it.toDomain() } }
            .catch { e ->
                Log.e(TAG, "searchHeroes DB error", e)
                emit(emptyList())
            }

    override suspend fun getHeroById(id: Int): Hero? =
        try { heroDao.getById(id)?.toDomain() }
        catch (e: Exception) { Log.e(TAG, "getById error", e); null }

    /**
     * Refresh priority:
     *   1. Remote API (if user has configured a URL in Settings)
     *   2. Bundled assets/heroes.json
     *   3. Existing DB cache (no-op — already emitted via [observeHeroes])
     */
    override suspend fun refreshHeroes(patch: String?): Resource<Unit> {
        // ── 1. Try remote API ──────────────────────────────────────────────
        val apiUrl = try { userPreferences.apiUrl.first() } catch (_: Exception) { "" }
        if (apiUrl.isNotBlank()) {
            val remoteResult = tryRemoteRefresh(apiUrl, patch)
            if (remoteResult is Resource.Success) return remoteResult
            Log.w(TAG, "Remote refresh failed — falling back to bundled assets")
        }

        // ── 2. Fall back to bundled asset ──────────────────────────────────
        return tryAssetRefresh()
    }

    private suspend fun tryRemoteRefresh(baseUrl: String, patch: String?): Resource<Unit> {
        return try {
            val service = NetworkModule.buildApiService(baseUrl, okHttpClient, gson)
                ?: return Resource.Error("Invalid API URL: $baseUrl")
            val snapshot = service.getMetaSnapshot(patch)
            heroDao.upsertAll(snapshot.heroes.map { it.toEntity() })
            metaSnapshotDao.upsert(snapshot.toEntity())
            Log.d(TAG, "Remote refresh OK — ${snapshot.heroes.size} heroes")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Remote refresh error", e)
            Resource.Error("Network error: ${e.message}", e)
        }
    }

    private suspend fun tryAssetRefresh(): Resource<Unit> {
        return try {
            val snapshot = assetDataSource.load()
                ?: return Resource.Error("Bundled heroes.json could not be parsed")
            val entities = snapshot.heroes.map { it.toEntity() }
            if (entities.isEmpty()) return Resource.Error("heroes.json contains no heroes")
            heroDao.upsertAll(entities)
            metaSnapshotDao.upsert(snapshot.toEntity())
            Log.d(TAG, "Asset refresh OK — ${entities.size} heroes")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Asset refresh error", e)
            Resource.Error("Failed to load bundled data: ${e.message}", e)
        }
    }

    override suspend fun getLastSyncedPatch(): String? =
        try { metaSnapshotDao.getLatest()?.patch }
        catch (e: Exception) { Log.e(TAG, "getLastSyncedPatch error", e); null }
}
