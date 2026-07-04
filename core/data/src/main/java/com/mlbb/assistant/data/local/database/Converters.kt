package com.mlbb.assistant.data.local.database

import androidx.room.TypeConverter
import com.mlbb.assistant.domain.model.CoreItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * todo.md §5 — Room [TypeConverter]s migrated from Gson to kotlinx.serialization,
 * matching the network layer (see `build.gradle.kts` comment "full migration
 * from Gson complete" and `NetworkModule`'s `retrofit-kotlinx-converter`).
 *
 * The Gson runtime dependency itself (`libs.gson`, `libs.retrofit.gson`) is
 * intentionally left in `build.gradle.kts`/`libs.versions.toml` until a
 * minified-build (`assembleRelease`) smoke test confirms no remaining call
 * site needs it and R8's keep rules are clean (todo.md §5, tracked separately
 * — this repo has no Gradle/R8 toolchain available to verify that here).
 */
class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromIntList(value: List<Int>): String = json.encodeToString(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromCoreItemList(value: List<CoreItem>): String = json.encodeToString(value)

    @TypeConverter
    fun toCoreItemList(value: String): List<CoreItem> =
        runCatching { json.decodeFromString<List<CoreItem>>(value) }.getOrDefault(emptyList())
}
