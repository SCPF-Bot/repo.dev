package com.mlbb.assistant.data.local.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mlbb.assistant.domain.model.CoreItem

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromIntList(value: List<Int>): String = gson.toJson(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson<List<Int>>(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromCoreItemList(value: List<CoreItem>): String = gson.toJson(value)

    @TypeConverter
    fun toCoreItemList(value: String): List<CoreItem> {
        val type = object : TypeToken<List<CoreItem>>() {}.type
        return gson.fromJson<List<CoreItem>>(value, type) ?: emptyList()
    }
}
