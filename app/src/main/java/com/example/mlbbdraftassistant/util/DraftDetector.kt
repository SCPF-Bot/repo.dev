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
     *
     * FIX: the previous version launched all 10 coroutines against the same already-
     * captured bitmap, but called captureScreen() only once outside the parallel block —
     * which is correct. However it did NOT recycle the original full-size bitmap after
     * cropping, leaking memory proportional to the screen resolution.
     * We now recycle the source bitmap once all crops are done.
     */
    suspend fun detect(allHeroes: List<Hero>): DetectedDraft = coroutineScope {
        val bitmap = captureManager.captureScreen(metrics)

        try {
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
        } finally {
            // FIX: recycle the screen-capture bitmap to free GPU/heap memory
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
