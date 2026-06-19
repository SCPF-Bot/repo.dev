package com.mlbb.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mlbb.assistant.R
import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.data.remote.dto.HeroDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class JsonParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    fun parseHeroes(): List<HeroEntity> = runCatching {
        val json = context.resources.openRawResource(R.raw.default_heroes)
            .bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<HeroDto>>() {}.type
        val dtos: List<HeroDto> = gson.fromJson(json, type) ?: emptyList()
        dtos.map { it.toEntity() }
    }.getOrDefault(emptyList())
}
