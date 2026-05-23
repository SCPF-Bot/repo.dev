package com.example.mlbbdraftassistant.util

import android.content.Context
import android.util.DisplayMetrics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class IconDetector(
    private val context: Context,
    private val captureManager: ScreenCaptureManager,
    private val metrics: DisplayMetrics
) {
    suspend fun detect(
        allHeroes: List<com.example.mlbbdraftassistant.data.model.Hero>
    ): DetectedDraft = coroutineScope {
        val bitmap = captureManager.captureScreen(metrics)

        suspendCancellableCoroutine { continuation ->
            val helper = ObjectDetectorHelper(context) { detections ->
                if (continuation.isActive) {
                    val result = mapDetectionsToDraft(detections, allHeroes)
                    helper.close()
                    continuation.resume(result)
                }
            }
            helper.detect(bitmap)

            continuation.invokeOnCancellation {
                helper.close()
            }
        }
    }

    private fun mapDetectionsToDraft(
        detections: List<Detection>,
        allHeroes: List<com.example.mlbbdraftassistant.data.model.Hero>
    ): DetectedDraft {
        val allies = MutableList<com.example.mlbbdraftassistant.data.model.Hero?>(5) { null }
        val enemies = MutableList<com.example.mlbbdraftassistant.data.model.Hero?>(5) { null }

        val slotMapper = SlotMapper(metrics.widthPixels, metrics.heightPixels)

        for (detection in detections) {
            val slotInfo = slotMapper.mapBoundingBox(detection.boundingBox) ?: continue

            val matchedHero = allHeroes.find {
                it.hero_name.equals(detection.label, ignoreCase = true)
            } ?: allHeroes.find {
                it.normalizedName == detection.label.lowercase().replace(Regex("[^a-z0-9]"), "")
            }

            val targetSlot = slotInfo.slot.coerceIn(0, 4)

            if (slotInfo.isAlly) {
                if (allies[targetSlot] == null) allies[targetSlot] = matchedHero
            } else {
                if (enemies[targetSlot] == null) enemies[targetSlot] = matchedHero
            }
        }

        return DetectedDraft(allies = allies, enemies = enemies)
    }
}