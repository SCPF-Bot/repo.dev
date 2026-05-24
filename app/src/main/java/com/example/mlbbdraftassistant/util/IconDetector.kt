package com.example.mlbbdraftassistant.util

import android.content.Context
import android.util.DisplayMetrics
import com.example.mlbbdraftassistant.data.model.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconDetector(
    private val context: Context,
    private val captureManager: ScreenCaptureManager,
    private val metrics: DisplayMetrics
) {
    /**
     * Captures the screen and maps detected hero icons to a [DetectedDraft].
     *
     * FIX: was using suspendCancellableCoroutine with a callback that referenced
     * 'helper' (a val) before the val was fully initialized — a forward-reference
     * bug that produces an UninitializedPropertyAccessException when
     * ObjectDetectorHelper.detect() fires the callback synchronously.
     * Also dispatches TFLite inference to Dispatchers.Default to avoid blocking
     * the main thread.
     */
    suspend fun detect(allHeroes: List<Hero>): DetectedDraft = withContext(Dispatchers.Default) {
        val bitmap = captureManager.captureScreen(metrics)
        val helper = ObjectDetectorHelper(context)
        return@withContext try {
            val detections = helper.detectSync(bitmap)
            mapDetectionsToDraft(detections, allHeroes)
        } finally {
            helper.close()
        }
    }

    private fun mapDetectionsToDraft(
        detections: List<Detection>,
        allHeroes: List<Hero>
    ): DetectedDraft {
        val allies = MutableList<Hero?>(5) { null }
        val enemies = MutableList<Hero?>(5) { null }

        val slotMapper = SlotMapper(metrics.widthPixels, metrics.heightPixels)

        for (detection in detections) {
            val slotInfo = slotMapper.mapBoundingBox(detection.boundingBox) ?: continue

            val matchedHero = allHeroes.find {
                it.hero_name.equals(detection.label, ignoreCase = true)
            } ?: allHeroes.find {
                it.normalizedName == detection.label.lowercase().replace(Regex("[^a-z0-9]"), "")
            }

            // FIX: slotInfo.slot is now guaranteed 0–4 (see SlotMapper), no coerceIn needed
            val targetSlot = slotInfo.slot

            if (slotInfo.isAlly) {
                if (allies[targetSlot] == null) allies[targetSlot] = matchedHero
            } else {
                if (enemies[targetSlot] == null) enemies[targetSlot] = matchedHero
            }
        }

        return DetectedDraft(allies = allies, enemies = enemies)
    }
}
