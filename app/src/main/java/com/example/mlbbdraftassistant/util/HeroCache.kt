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
     */
    fun save(heroes: List<Hero>) {
        cacheFile.writeText(gson.toJson(heroes))
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