package com.mlbb.assistant.utils

import android.content.Context
import com.mlbb.assistant.R
import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.data.remote.dto.HeroDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Parses the bundled hero JSON asset using kotlinx.serialization.
 *
 * P3-01: Migrated from Gson to kotlinx.serialization. [ignoreUnknownKeys] ensures
 * bundled JSON with extra server-side fields never throws during parsing. The raw
 * resource [R.raw.default_heroes] must remain a JSON array of [HeroDto] objects.
 */
class JsonParser @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseHeroes(): List<HeroEntity> = runCatching {
        val dtos = parseDtos()
        dtos.map { it.toEntity() }
    }.getOrDefault(emptyList())

    /**
     * Returns a map of heroId → official portrait URL sourced directly from the
     * bundled [R.raw.default_heroes] JSON asset.
     *
     * Use this as the authoritative URL source when downloading portraits — the Room
     * DB may hold stale or empty [imageUrl] values (e.g. after a schema migration or
     * a partial remote sync), whereas the bundled JSON always contains the correct
     * official CDN URLs.
     *
     * Heroes with a blank [HeroDto.imageUrl] are excluded from the result so callers
     * can safely skip them without an extra blank-check.
     */
    fun buildPortraitUrlIndex(): Map<Int, String> = runCatching {
        parseDtos()
            .filter { it.imageUrl.isNotBlank() }
            .associate { it.id to it.imageUrl }
    }.getOrDefault(emptyMap())

    private fun parseDtos(): List<HeroDto> {
        val text = context.resources.openRawResource(R.raw.default_heroes)
            .bufferedReader()
            .use { it.readText() }
        return json.decodeFromString<List<HeroDto>>(text)
    }
}
