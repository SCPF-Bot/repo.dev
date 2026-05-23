package com.example.mlbbdraftassistant.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CropRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun toRect(screenWidth: Int, screenHeight: Int): Rect {
        return Rect(
            (left * screenWidth).toInt(),
            (top * screenHeight).toInt(),
            (right * screenWidth).toInt(),
            (bottom * screenHeight).toInt()
        )
    }
}

object CropRegions {

    // Default calibration (portrait, 9:19.5 aspect ratio)
    val DEFAULT_ALLY_SLOTS = listOf(
        CropRegion(0.02f, 0.12f, 0.28f, 0.20f),
        CropRegion(0.02f, 0.30f, 0.28f, 0.38f),
        CropRegion(0.02f, 0.48f, 0.28f, 0.56f),
        CropRegion(0.02f, 0.66f, 0.28f, 0.74f),
        CropRegion(0.02f, 0.84f, 0.28f, 0.92f),
    )

    val DEFAULT_ENEMY_SLOTS = listOf(
        CropRegion(0.72f, 0.12f, 0.98f, 0.20f),
        CropRegion(0.72f, 0.30f, 0.98f, 0.38f),
        CropRegion(0.72f, 0.48f, 0.98f, 0.56f),
        CropRegion(0.72f, 0.66f, 0.98f, 0.74f),
        CropRegion(0.72f, 0.84f, 0.98f, 0.92f),
    )

    var allySlots: List<CropRegion> = DEFAULT_ALLY_SLOTS
    var enemySlots: List<CropRegion> = DEFAULT_ENEMY_SLOTS

    private val gson = Gson()

    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)
        val allyJson = prefs.getString("ally_regions", null)
        val enemyJson = prefs.getString("enemy_regions", null)

        allySlots = if (allyJson != null) {
            val type = object : TypeToken<List<CropRegion>>() {}.type
            gson.fromJson(allyJson, type)
        } else {
            DEFAULT_ALLY_SLOTS
        }

        enemySlots = if (enemyJson != null) {
            val type = object : TypeToken<List<CropRegion>>() {}.type
            gson.fromJson(enemyJson, type)
        } else {
            DEFAULT_ENEMY_SLOTS
        }
    }

    fun saveToPrefs(context: Context, allyRegions: List<CropRegion>, enemyRegions: List<CropRegion>) {
        val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ally_regions", gson.toJson(allyRegions))
            .putString("enemy_regions", gson.toJson(enemyRegions))
            .apply()
    }

    fun cropBitmap(bitmap: Bitmap, region: CropRegion): Bitmap {
        val rect = region.toRect(bitmap.width, bitmap.height)
        val clamped = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height)
        )
        if (clamped.width() <= 0 || clamped.height() <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, clamped.left, clamped.top, clamped.width(), clamped.height())
    }
}