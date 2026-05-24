package com.example.mlbbdraftassistant.util

import android.content.Context
import com.example.mlbbdraftassistant.data.model.Hero
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class HeroCache(private val context: Context) {

    private val gson = Gson()
    private val cacheFile: File
        get() = File(context.filesDir, "hero_cache.json")
    private val timestampFile: File
        get() = File(context.filesDir, "hero_cache_timestamp.txt")

    /**
     * Save the hero list to disk and update the timestamp.
     *
     * FIX: write to a temp file then atomically rename, so a crash mid-write
     * never leaves a corrupted cache file that the app cannot recover from.
     */
    fun save(heroes: List<Hero>) {
        val tmp = File(context.filesDir, "hero_cache.json.tmp")
        tmp.writeText(gson.toJson(heroes))
        tmp.renameTo(cacheFile)
        timestampFile.writeText(System.currentTimeMillis().toString())
    }

    /**
     * Load cached heroes if the cache file exists.
     */
    fun load(): List<Hero>? {
        if (!cacheFile.exists()) return null
        return try {
            val json = cacheFile.readText()
            val type = object : TypeToken<List<Hero>>() {}.type
            gson.fromJson<List<Hero>>(json, type)
        } catch (e: Exception) {
            // FIX: corrupt cache file — delete it so the next launch fetches fresh data
            clear()
            null
        }
    }

    /**
     * Return true if the cache is younger than [maxAgeMillis].
     */
    fun isFresh(maxAgeMillis: Long = 24 * 60 * 60 * 1000L): Boolean {
        if (!timestampFile.exists()) return false
        val lastUpdate = timestampFile.readText().toLongOrNull() ?: return false
        return System.currentTimeMillis() - lastUpdate < maxAgeMillis
    }

    /**
     * Clear the cache manually (e.g., when user forces a refresh).
     */
    fun clear() {
        cacheFile.delete()
        timestampFile.delete()
    }
}
