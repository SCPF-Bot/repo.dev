package com.mlbb.assistant.utils

import android.content.Context
import com.mlbb.assistant.core.data.R
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
     *
     * Memoized on first successful build. [R.raw.default_heroes] is a bundled, immutable
     * APK resource, so re-parsing it is pure waste — but this was previously re-parsed on
     * every call, including once per hero from
     * Previously called during portrait asset preparation. During
     * on-demand slot-aware hash preloading that method can run once per hero per
     * [com.mlbb.assistant.capture.PortraitMatcher.preloadHashes] pass, so re-decoding the
     * full JSON array + rebuilding the map each time added needless CPU work on the
     * portrait-download hot path. A failed parse is intentionally NOT cached (`null`),
     * so a transient decode error doesn't permanently pin an empty index for the
     * lifetime of this instance — the next call retries the parse.
     */
    @Volatile
    private var cachedPortraitUrlIndex: Map<Int, String>? = null

    fun buildPortraitUrlIndex(): Map<Int, String> {
        cachedPortraitUrlIndex?.let { return it }
        val built = runCatching {
            parseDtos()
                .filter { it.imageUrl.isNotBlank() }
                .associate { it.id to it.imageUrl }
        }.getOrNull()
        if (built != null) cachedPortraitUrlIndex = built
        return built ?: emptyMap()
    }

    private fun parseDtos(): List<HeroDto> {
        val text = context.resources.openRawResource(R.raw.default_heroes)
            .bufferedReader()
            .use { it.readText() }
        return json.decodeFromString<List<HeroDto>>(text)
    }
}
