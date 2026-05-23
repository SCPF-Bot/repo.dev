package com.example.mlbbdraftassistant.util

import android.util.DisplayMetrics
import com.example.mlbbdraftassistant.data.model.Hero

data class DetectedDraft(
    val allies: List<Hero?>,
    val enemies: List<Hero?>
)

class DraftDetector(
    private val captureManager: ScreenCaptureManager,
    private val metrics: DisplayMetrics
) {

    /**
     * Captures the screen, crops each slot, runs OCR, and maps results to heroes.
     */
    suspend fun detect(allHeroes: List<Hero>): DetectedDraft {
        val bitmap = captureManager.captureScreen(metrics)

        val allies = CropRegions.ALLY_SLOTS.map { region ->
            val crop = CropRegions.cropBitmap(bitmap, region)
            val text = OcrEngine.recognize(crop)
            text?.let { FuzzyMatcher.match(it, allHeroes) }
        }

        val enemies = CropRegions.ENEMY_SLOTS.map { region ->
            val crop = CropRegions.cropBitmap(bitmap, region)
            val text = OcrEngine.recognize(crop)
            text?.let { FuzzyMatcher.match(it, allHeroes) }
        }

        return DetectedDraft(allies = allies, enemies = enemies)
    }
}