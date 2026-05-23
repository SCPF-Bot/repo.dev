package com.example.mlbbdraftassistant.util

import android.util.DisplayMetrics
import com.example.mlbbdraftassistant.data.model.Hero
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    suspend fun detect(allHeroes: List<Hero>): DetectedDraft = coroutineScope {
        val bitmap = captureManager.captureScreen(metrics)

        val allyDeferred = CropRegions.allySlots.map { region ->
            async {
                val crop = CropRegions.cropBitmap(bitmap, region)
                val text = OcrEngine.recognize(crop)
                text?.let { FuzzyMatcher.match(it, allHeroes) }
            }
        }

        val enemyDeferred = CropRegions.enemySlots.map { region ->
            async {
                val crop = CropRegions.cropBitmap(bitmap, region)
                val text = OcrEngine.recognize(crop)
                text?.let { FuzzyMatcher.match(it, allHeroes) }
            }
        }

        val allies = allyDeferred.awaitAll()
        val enemies = enemyDeferred.awaitAll()

        DetectedDraft(allies = allies, enemies = enemies)
    }
}