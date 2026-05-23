package com.example.mlbbdraftassistant.util

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Defines crop regions for ally and enemy hero names on the draft screen.
 * All values are fractions (0.0 – 1.0) of screen width/height.
 */
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

/**
 * Default calibration (portrait, 9:19.5 aspect ratio).
 * These values must be tuned by the user via the calibration wizard.
 */
object CropRegions {

    // Ally team (blue side, left of screen)
    val ALLY_SLOTS = listOf(
        CropRegion(0.02f, 0.12f, 0.28f, 0.20f),  // slot 1
        CropRegion(0.02f, 0.30f, 0.28f, 0.38f),  // slot 2
        CropRegion(0.02f, 0.48f, 0.28f, 0.56f),  // slot 3
        CropRegion(0.02f, 0.66f, 0.28f, 0.74f),  // slot 4
        CropRegion(0.02f, 0.84f, 0.28f, 0.92f),  // slot 5
    )

    // Enemy team (red side, right of screen)
    val ENEMY_SLOTS = listOf(
        CropRegion(0.72f, 0.12f, 0.98f, 0.20f),  // slot 1
        CropRegion(0.72f, 0.30f, 0.98f, 0.38f),  // slot 2
        CropRegion(0.72f, 0.48f, 0.98f, 0.56f),  // slot 3
        CropRegion(0.72f, 0.66f, 0.98f, 0.74f),  // slot 4
        CropRegion(0.72f, 0.84f, 0.98f, 0.92f),  // slot 5
    )

    fun cropBitmap(bitmap: Bitmap, region: CropRegion): Bitmap {
        val rect = region.toRect(bitmap.width, bitmap.height)
        // Clamp rect within bitmap bounds
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