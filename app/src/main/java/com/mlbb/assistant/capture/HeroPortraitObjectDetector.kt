package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.RectF
import timber.log.Timber

/**
 * Hero portrait region detector using ML Kit Object Detection (custom TFLite model).
 *
 * todo.md §5.9: ML Kit custom model pipeline.
 *
 * MODEL REQUIREMENT
 * -----------------
 * Place the trained model at [MODEL_ASSET_PATH] inside the app's `assets/` directory.
 * The model must output bounding boxes for hero portrait regions, labelled with a
 * single class per detection.  Until the model is available, [detectPortraitRegions]
 * returns an empty list, allowing callers to fall back to the existing slot-based
 * coordinate system in [SlotRegions].
 *
 * INTEGRATION STATUS
 * ------------------
 * ML Kit custom object detection is currently **stubbed** pending the training pipeline
 * (todo.md §5.9). All calls return an empty list, which triggers the coordinate-based
 * [SlotRegions] fallback in every caller.  No ML Kit dependency is required at this
 * stage; restore the implementation once the TFLite model is available.
 */
class HeroPortraitObjectDetector {

    companion object {
        private const val MODEL_ASSET_PATH = "mlbb_hero_detector.tflite"
        private const val TAG = "HeroPortraitOD"
    }

    data class DetectedPortrait(
        val boundingBox: RectF,
        val confidence: Float,
        val label: String?
    )

    /**
     * Detects hero portrait regions in [frame] using the custom ML Kit model.
     *
     * Currently stubbed — returns an empty list until the TFLite model is trained
     * and placed at [MODEL_ASSET_PATH] (todo.md §5.9). Callers fall back to
     * [SlotRegions] coordinate-based detection when the list is empty.
     */
    suspend fun detectPortraitRegions(
        frame: Bitmap,
        assetManager: android.content.res.AssetManager
    ): List<DetectedPortrait> {
        Timber.d("$TAG: model stub active — returning empty list (todo.md §5.9)")
        return emptyList()
    }

    /** No-op until the model is integrated. */
    fun close() = Unit
}
