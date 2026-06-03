package com.mlbbassistant.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.mlbbassistant.data.api.dto.MetaSnapshotDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads hero data from the bundled [assets/heroes.json].
 * Never throws — returns null if the file is missing or malformed.
 */
@Singleton
class AssetHeroDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val FILE = "heroes.json"
        private const val TAG  = "AssetHeroDataSource"
    }

    fun load(): MetaSnapshotDto? {
        return try {
            val json = context.assets.open(FILE).bufferedReader().use { it.readText() }
            gson.fromJson(json, MetaSnapshotDto::class.java)
        } catch (e: JsonParseException) {
            Log.e(TAG, "heroes.json is malformed", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read heroes.json from assets", e)
            null
        }
    }
}
