package com.example.mlbbdraftassistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.util.HeroCache
import com.example.mlbbdraftassistant.util.HeroParser
import com.example.mlbbdraftassistant.util.PrefKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HeroRepositoryImpl(private val context: Context) : HeroRepository {

    private val cache = HeroCache(context)
    private val _heroes = MutableStateFlow<List<Hero>>(emptyList())
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // Public observable list
    override fun observeHeroes(): Flow<List<Hero>> = _heroes.asStateFlow()

    override suspend fun refreshHeroData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // FIX: read the user-configurable endpoint from SharedPreferences instead of
            // always hitting the hard-coded fallback URL. The SettingsActivity lets users
            // change PrefKeys.API_ENDPOINT, but the original code ignored that preference.
            val url = prefs.getString(PrefKeys.API_ENDPOINT, DEFAULT_API_URL)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_API_URL

            val json = fetchJsonFromUrl(url)
            val heroes = HeroParser.parse(json)
                ?: return@withContext Result.failure(Exception("Parse error: response was not valid JSON hero list"))
            cache.save(heroes)
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
            // Show stale data immediately while the fresh fetch runs
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
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        // FIX: check HTTP response code before reading the body so a 4xx/5xx
        // response doesn't silently return an empty/error string that then
        // fails JSON parsing with an unhelpful "Parse error".
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw Exception("HTTP $code from $urlString")
            }
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            reader.use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val DEFAULT_API_URL =
            "https://raw.githubusercontent.com/ridwaanhall/api-mobilelegends/main/hero.json"
    }
}
