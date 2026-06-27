package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.custom.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

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
 * INTEGRATION GUARD
 * -----------------
 * Model initialisation is attempted lazily on first call and wrapped in [runCatching].
 * If the asset is missing (file not found → IllegalArgumentException) or the model
 * fails to load for any other reason, [modelAvailable] stays false and all calls
 * fall through to the fallback path immediately — no crash, no retry overhead.
 *
 * STREAM vs. SINGLE_IMAGE MODE
 * ----------------------------
 * [CustomObjectDetectorOptions.STREAM_MODE] is configured for overlay use (continuous
 * capture). Use [CustomObjectDetectorOptions.SINGLE_IMAGE_MODE] for one-shot analysis.
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

    // Initialised lazily on first call; stays null if model is missing.
    private var objectDetector: com.google.mlkit.vision.objects.ObjectDetector? = null
    private var modelAvailable = false
    private var initAttempted = false

    /**
     * Attempts to initialise the ML Kit custom object detector from [MODEL_ASSET_PATH].
     *
     * Safe to call multiple times — after the first attempt the result is cached.
     * Returns true when the detector is ready, false otherwise.
     */
    private fun ensureDetectorInitialised(assetManager: android.content.res.AssetManager): Boolean {
        if (initAttempted) return modelAvailable
        initAttempted = true

        modelAvailable = runCatching {
            // Verify the model file exists in assets before handing it to ML Kit.
            // ML Kit throws IllegalArgumentException (not FileNotFoundException) for missing
            // assets, so this pre-check gives a clearer log message.
            assetManager.list("")?.contains(MODEL_ASSET_PATH)
                ?: false
        }.getOrDefault(false)

        if (!modelAvailable) {
            Timber.w("$TAG: model '$MODEL_ASSET_PATH' not found in assets — " +
                "falling back to coordinate-based slot detection (todo.md §5.9).")
            return false
        }

        modelAvailable = runCatching {
            val localModel = LocalModel.Builder()
                .setAssetFilePath(MODEL_ASSET_PATH)
                .build()

            val options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .setMaxPerObjectLabelCount(1)
                .build()

            objectDetector = ObjectDetection.getClient(options)
            Timber.i("$TAG: ML Kit object detector initialised successfully.")
            true
        }.getOrElse { e ->
            Timber.e(e, "$TAG: failed to initialise ML Kit object detector")
            false
        }

        return modelAvailable
    }

    /**
     * Detects hero portrait regions in [frame] using the custom ML Kit model.
     *
     * @param frame        Full or cropped screen frame to analyse.
     * @param assetManager Android [android.content.res.AssetManager] to load the TFLite model.
     * @return List of detected portrait regions, or empty list when the model is unavailable.
     *         Callers should fall back to [SlotRegions] coordinate-based detection when empty.
     */
    suspend fun detectPortraitRegions(
        frame: Bitmap,
        assetManager: android.content.res.AssetManager
    ): List<DetectedPortrait> = withContext(Dispatchers.Default) {
        if (!ensureDetectorInitialised(assetManager)) return@withContext emptyList()

        val detector = objectDetector ?: return@withContext emptyList()

        runCatching {
            val image = InputImage.fromBitmap(frame, 0)
            suspendCancellableCoroutine { cont ->
                detector.process(image)
                    .addOnSuccessListener { detectedObjects ->
                        val results = detectedObjects.mapNotNull { obj ->
                            val label  = obj.labels.firstOrNull()
                            val bounds = obj.boundingBox
                            val score  = label?.confidence ?: 0f
                            if (score >= 0.5f) {
                                DetectedPortrait(
                                    boundingBox = RectF(bounds),
                                    confidence  = score,
                                    label       = label?.text
                                )
                            } else null
                        }
                        cont.resume(results)
                    }
                    .addOnFailureListener { e ->
                        Timber.w(e, "$TAG: object detection failed on this frame")
                        cont.resume(emptyList())
                    }
                cont.invokeOnCancellation { /* ML Kit tasks are not cancellable; result ignored */ }
            }
        }.getOrElse { e ->
            Timber.e(e, "$TAG: unexpected error during portrait detection")
            emptyList()
        }
    }

    /** Releases the ML Kit detector client when no longer needed. */
    fun close() {
        runCatching { objectDetector?.close() }
        objectDetector = null
    }
}
