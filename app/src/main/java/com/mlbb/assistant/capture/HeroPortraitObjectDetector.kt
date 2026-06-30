package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.RectF
import timber.log.Timber

/**
 * Hero portrait **region** detector using ML Kit Object Detection (custom TFLite model).
 *
 * ### This class vs. [HeroClassifier]
 *
 * There are two distinct ML tasks in the CV pipeline:
 *
 * | Task | Class | Model type | Current status |
 * |---|---|---|---|
 * | **Classification** — *which* hero is in a pre-cropped slot | [HeroClassifier] | MobileNetV3Small softmax classifier (`mlbb_hero_classifier.tflite`) | ✅ Integrated |
 * | **Detection** — *where* hero portrait regions are in the full frame | [HeroPortraitObjectDetector] | SSD / YOLO object-detection model (not yet trained) | ⚙️ Stubbed |
 *
 * [HeroClassifier] is the fully wired primary matching path in [PortraitMatcher].
 * This class ([HeroPortraitObjectDetector]) is a stub for a separate, not-yet-trained
 * SSD-style object-detection model that would locate hero portrait bounding boxes in
 * the raw MediaProjection frame, replacing the coordinate-based [SlotRegions] approach.
 * Training that model requires 500+ annotated portrait crops (see `roadmap.md` RA-05).
 *
 * ### Integration
 * [detectPortraitRegions] returns an empty list while the stub is active. Callers
 * fall back to [SlotRegions] coordinate-based detection, which is the production path.
 *
 * ### MODEL REQUIREMENT (when training is complete)
 * Place the trained SSD model at [MODEL_ASSET_PATH] and replace [detectPortraitRegions]
 * with the ML Kit ObjectDetector API call.  The model must output bounding boxes with a
 * single class label per detection.
 */
class HeroPortraitObjectDetector {

    companion object {
        private const val MODEL_ASSET_PATH = "mlbb_hero_detector.tflite"
        private const val TAG = "HeroPortraitOD"
    }

    data class DetectedPortrait(
        val boundingBox: RectF,
        val confidence: Float,
        val label: String?,
    )

    /**
     * Detects hero portrait bounding boxes in [frame] using the custom ML Kit
     * object-detection model.
     *
     * **Currently stubbed** — returns an empty list until the SSD/YOLO detection
     * model is trained and placed at [MODEL_ASSET_PATH] (see `roadmap.md` RA-05).
     * Callers fall back to [SlotRegions] coordinate-based slot scanning when the
     * list is empty.
     *
     * Note: hero *identification* (which hero) is handled by [HeroClassifier], not
     * this class. This class only locates portrait *regions* in the raw frame.
     */
    suspend fun detectPortraitRegions(
        frame: Bitmap,
        assetManager: android.content.res.AssetManager,
    ): List<DetectedPortrait> {
        Timber.d("$TAG: detection model stub active — returning empty list (roadmap RA-05)")
        return emptyList()
    }

    /** No-op until the detection model is integrated. */
    fun close() = Unit
}
