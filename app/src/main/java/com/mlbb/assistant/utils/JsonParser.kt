package com.mlbb.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.data.remote.dto.HeroDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and deserialises the bundled hero seed JSON from the app's assets folder.
 *
 * The asset file (`assets/heroes_seed.json`) ships with the APK and provides
 * an offline fallback when the remote API is unreachable and the database is
 * empty (first launch with no internet).
 *
 * Expected asset format: JSON array of [HeroDto] objects.
 */
@Singleton
class JsonParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val ASSET_FILE = "heroes_seed.json"
    }

    /**
     * Parses the bundled JSON and returns a list of [HeroEntity] objects
     * ready to be inserted into Room. Returns an empty list on any error.
     */
    suspend fun parseHeroes(): List<HeroEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<HeroDto>>() {}.type
            val dtos: List<HeroDto> = gson.fromJson(json, type)
            dtos.map { it.toEntity() }
        }.getOrElse { e ->
            Timber.e(e, "JsonParser: failed to parse $ASSET_FILE")
            emptyList()
        }
    }
}
