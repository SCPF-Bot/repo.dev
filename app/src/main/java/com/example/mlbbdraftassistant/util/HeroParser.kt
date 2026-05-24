package com.example.mlbbdraftassistant.util

import com.example.mlbbdraftassistant.data.model.Hero
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HeroParser {

    private val gson = Gson()

    fun parse(jsonString: String): List<Hero>? {
        return try {
            val type = object : TypeToken<List<Hero>>() {}.type
            gson.fromJson<List<Hero>>(jsonString, type)
        } catch (e: Exception) {
            null
        }
    }
}