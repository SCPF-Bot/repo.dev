package com.mlbb.assistant.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.mlbb.assistant.data.local.database.HeroDao
import com.mlbb.assistant.data.remote.api.MetaApi
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import com.mlbb.assistant.utils.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * TD-06 IO audit: all suspend functions explicitly use [withContext](Dispatchers.IO)
 * as a defensive belt-and-suspenders guard.  Room and Retrofit both emit on IO
 * by default, but wrapping ensures correctness even if callers forget to dispatch
 * and prevents accidental main-thread DB or network calls.
 *
 * Flow-returning functions remain unwrapped — their emissions are already
 * delivered on IO by Room's built-in executor.
 *
 * P1-02 fix: retry logic has been moved here from the OkHttp interceptor layer.
 * The previous [RetryInterceptor] used [Thread.sleep] which blocked an OkHttp
 * dispatcher thread for up to 4 seconds per retry. The new [syncWithRetry]
 * uses coroutine [delay] — non-blocking and cancellation-aware — so the thread
 * is released during the back-off window and concurrent requests are unaffected.
 */
class HeroRepositoryImpl @Inject constructor(
    private val heroDao: HeroDao,
    private val metaApi: MetaApi,
    private val jsonParser: JsonParser
) : HeroRepository {

    companion object {
        private const val MAX_SYNC_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 500L
    }

    // Flow-based queries: Room delivers on IO automatically.
    override fun getHeroes(): Flow<List<Hero>> =
        heroDao.getAllHeroes().map { entities -> entities.map { it.toDomain() } }

    override fun searchHeroes(query: String): Flow<List<Hero>> =
        heroDao.searchHeroes(query).map { entities -> entities.map { it.toDomain() } }

    // Suspend queries: wrapped with withContext(Dispatchers.IO) for explicit safety.
    override suspend fun getHeroById(id: Int): Hero? =
        withContext(Dispatchers.IO) { heroDao.getHeroById(id)?.toDomain() }

    override suspend fun getHeroesByIds(ids: List<Int>): List<Hero> =
        withContext(Dispatchers.IO) { heroDao.getHeroesByIds(ids).map { it.toDomain() } }

    override suspend fun getTopMetaHeroes(limit: Int): List<Hero> =
        withContext(Dispatchers.IO) { heroDao.getTopMetaHeroes(limit).map { it.toDomain() } }

    // ── TD-10: Paging3 ────────────────────────────────────────────────────────

    /**
     * Constructs a [Pager] backed by Room's auto-generated [PagingSource].
     *
     * - [enablePlaceholders] = false: avoids item-count queries on every page
     *   load, which would double the DB read cost for large hero rosters.
     * - [prefetchDistance] = pageSize / 2: triggers the next page load when
     *   the user is halfway through the current page for smooth scrolling.
     */
    override fun getHeroesPaged(
        query:    String,
        lane:     String,
        pageSize: Int
    ): Flow<PagingData<Hero>> = Pager(
        config = PagingConfig(
            pageSize         = pageSize,
            enablePlaceholders = false,
            prefetchDistance = pageSize / 2
        ),
        pagingSourceFactory = { heroDao.getHeroesPaged(query, lane) }
    ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    // ── TD-06 / P1-02: IO-safe suspend functions with coroutine retry ─────────

    /**
     * Syncs hero meta data from the network with coroutine-based retry back-off.
     *
     * Retry policy: up to [MAX_SYNC_RETRIES] attempts with exponential back-off
     * (500 ms, 1 000 ms, 1 500 ms). Uses [delay] so the calling coroutine is
     * suspended — not blocked — during back-off windows.
     *
     * On total failure falls back to the local DB; if the DB is also empty,
     * seeds from the bundled JSON asset.
     */
    override suspend fun syncHeroes(): Unit = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null

        repeat(MAX_SYNC_RETRIES) { attempt ->
            runCatching {
                Timber.d("syncHeroes: fetching meta snapshot (attempt ${attempt + 1}/$MAX_SYNC_RETRIES)")
                val snapshot = metaApi.getMetaSnapshot()
                heroDao.replaceAll(snapshot.heroes.map { it.toEntity() })
                Timber.i("syncHeroes: synced ${snapshot.heroes.size} heroes from network")
                return@withContext   // success — exit early
            }.onFailure { e ->
                lastError = e
                Timber.w(e, "syncHeroes: attempt ${attempt + 1} failed")
                if (attempt < MAX_SYNC_RETRIES - 1) {
                    delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                }
            }
        }

        // All network attempts exhausted — fall back to local data.
        Timber.w(lastError, "syncHeroes: all $MAX_SYNC_RETRIES attempts failed — using local data")
        val existing = heroDao.getTopMetaHeroes(1)
        if (existing.isEmpty()) {
            Timber.d("syncHeroes: DB empty, seeding from bundled JSON")
            seedFromJson()
        }
    }

    private suspend fun seedFromJson() {
        val heroes = withContext(Dispatchers.IO) { jsonParser.parseHeroes() }
        if (heroes.isNotEmpty()) {
            heroDao.replaceAll(heroes)
            Timber.i("seedFromJson: inserted ${heroes.size} heroes from bundled JSON")
        } else {
            Timber.w("seedFromJson: bundled JSON parse returned empty list")
        }
    }
}
