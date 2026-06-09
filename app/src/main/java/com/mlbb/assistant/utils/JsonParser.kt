// File: app/src/main/java/com/mlbb/assistant/utils/JsonParser.kt
package com.mlbb.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mlbb.assistant.data.remote.dto.HeroDto
import java.io.IOException

object JsonParser {
    fun loadHeroesFromAssets(context: Context): List<HeroDto> {
        return try {
            val jsonString = context.resources.openRawResource(com.mlbb.assistant.R.raw.default_heroes)
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<HeroDto>>() {}.type
            Gson().fromJson(jsonString, type)
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }
}