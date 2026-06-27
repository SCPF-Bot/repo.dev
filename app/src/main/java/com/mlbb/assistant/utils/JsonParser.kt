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
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseHeroes(): List<HeroEntity> = runCatching {
        val text = context.resources.openRawResource(R.raw.default_heroes)
            .bufferedReader()
            .use { it.readText() }
        val dtos = json.decodeFromString<List<HeroDto>>(text)
        dtos.map { it.toEntity() }
    }.getOrDefault(emptyList())
}
