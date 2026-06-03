package com.mlbbassistant.data.db

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val listType = object : TypeToken<List<Int>>() {}.type

    @TypeConverter
    fun fromIntList(value: List<Int>?): String =
        try { gson.toJson(value ?: emptyList<Int>()) }
        catch (e: Exception) { "[]" }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            Log.w("Converters", "Bad int-list JSON: $value")
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
