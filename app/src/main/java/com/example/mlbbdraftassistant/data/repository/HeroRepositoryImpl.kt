package com.example.mlbbdraftassistant.data.repository

import android.content.Context
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.util.HeroCache
import com.example.mlbbdraftassistant.util.HeroParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HeroRepositoryImpl(context: Context) : HeroRepository {

    private val cache = HeroCache(context)
    private val _heroes = MutableStateFlow<List<Hero>>(emptyList())

    // Public observable list
    override fun observeHeroes(): Flow<List<Hero>> = _heroes.asStateFlow()

    override suspend fun refreshHeroData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Fetch fresh data from network
            val json = fetchJsonFromUrl(API_URL)
            val heroes = HeroParser.parse(json) ?: return@withContext Result.failure(Exception("Parse error"))
            // Save to cache
            cache.save(heroes)
            // Update state
            _heroes.value = heroes
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHeroById(id: Int): Hero? {
        return _heroes.value.find { it.hero_id == id }
    }

    /**
     * Initial load: first from cache, then network if cache is stale or missing.
     * Call this once from a ViewModel or Application onCreate.
     */
    suspend fun initialize() {
        val cached = cache.load()
        if (cached != null && cache.isFresh()) {
            _heroes.value = cached
        } else {
            // If cache exists but stale, still show it while fetching fresh
            if (cached != null) {
                _heroes.value = cached
            }
            refreshHeroData()
        }
    }

    private fun fetchJsonFromUrl(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        return try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            response.toString()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API_URL = "https://raw.githubusercontent.com/ridwaanhall/api-mobilelegends/main/hero.json"
    }
}