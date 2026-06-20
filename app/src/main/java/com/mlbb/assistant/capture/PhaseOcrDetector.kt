package com.mlbb.assistant.capture

import android.graphics.Bitmap
import com.mlbb.assistant.capture.PhaseDetector.DetectedPhase

/**
 * Section 3.2.4 — Phase disambiguation via ML Kit On-Device OCR.
 *
 * [PhaseDetector] uses colour histogram heuristics which can misclassify
 * edge-case frames (e.g. dark loading screens vs. ban phase).
 * [PhaseOcrDetector] reads visible text labels ("BAN", "PICK", "READY")
 * to produce a higher-confidence classification.
 *
 * Runtime dependency: `com.google.mlkit:text-recognition` (optional).
 * If ML Kit is not available at runtime the detector returns UNKNOWN with
 * confidence 0 so the caller falls back to [PhaseDetector].
 *
 * The OCR scan is intentionally restricted to the top 20 % of the frame
 * where MLBB prints phase labels, keeping latency low.
 */
object PhaseOcrDetector {

    data class OcrResult(val phase: DetectedPhase, val confidence: Float)

    /**
     * Region (normalised) containing the phase label text.
     * MLBB prints "BAN PHASE", "PICK PHASE", "TRADING" etc. in this band.
     */
    private val TEXT_REGION = SlotRegionF(left = 0.25f, top = 0.00f, right = 0.75f, bottom = 0.18f)

    /**
     * Attempts to read the phase label from [frame] using ML Kit Text
     * Recognition.
     *
     * Returns [OcrResult] with [DetectedPhase.UNKNOWN] and confidence 0 when:
     *   - ML Kit is unavailable on the device
     *   - The crop region is empty or fails
     *   - No recognised keyword matches
     *
     * Callers should combine this result with [PhaseDetector.detect] results,
     * preferring OCR when confidence ≥ 0.7.
     */
    fun detect(frame: Bitmap, onResult: (OcrResult) -> Unit) {
        val crop = runCatching { SlotRegions.cropSlot(frame, TEXT_REGION) }.getOrNull()
        if (crop == null) {
            onResult(OcrResult(DetectedPhase.UNKNOWN, 0f))
            return
        }

        runCatching {
            // ML Kit text recognition is loaded reflectively so this module
            // compiles without a hard dependency on the ML Kit artifact.
            // The artifact must be declared in app/build.gradle when enabling:
            //   implementation "com.google.mlkit:text-recognition:16.0.0"
            val recognizerClass = Class.forName("com.google.mlkit.vision.text.TextRecognition")
            val optionsClass    = Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions")
            val defaultOpts     = optionsClass.getDeclaredField("DEFAULT_OPTIONS").also { it.isAccessible = true }.get(null)
            val getClientMethod = recognizerClass.getMethod("getClient", Class.forName("com.google.mlkit.vision.text.TextRecognizerOptions"))
            val recognizer      = getClientMethod.invoke(null, defaultOpts)

            val imageClass    = Class.forName("com.google.mlkit.vision.common.InputImage")
            val fromBmpMethod = imageClass.getMethod("fromBitmap", Bitmap::class.java, Int::class.java)
            val inputImage    = fromBmpMethod.invoke(null, crop, 0)

            val processMethod = recognizer.javaClass.getMethod("process",
                Class.forName("com.google.mlkit.vision.common.InputImage"))
            val task = processMethod.invoke(recognizer, inputImage)

            val addListenerMethod = task.javaClass.getMethod("addOnSuccessListener",
                com.google.android.gms.tasks.OnSuccessListener::class.java)
            addListenerMethod.invoke(task, com.google.android.gms.tasks.OnSuccessListener<Any> { result ->
                crop.recycle()
                val getText = result.javaClass.getMethod("getText")
                val text    = (getText.invoke(result) as? String)?.uppercase() ?: ""
                onResult(classifyText(text))
            })

            val addFailureMethod = task.javaClass.getMethod("addOnFailureListener",
                com.google.android.gms.tasks.OnFailureListener::class.java)
            addFailureMethod.invoke(task, com.google.android.gms.tasks.OnFailureListener {
                crop.recycle()
                onResult(OcrResult(DetectedPhase.UNKNOWN, 0f))
            })
        }.onFailure {
            crop.recycle()
            onResult(OcrResult(DetectedPhase.UNKNOWN, 0f))
        }
    }

    private fun classifyText(upperText: String): OcrResult = when {
        "BAN"     in upperText -> OcrResult(DetectedPhase.BAN,     0.90f)
        "PICK"    in upperText -> OcrResult(DetectedPhase.PICK,    0.90f)
        "TRADING" in upperText -> OcrResult(DetectedPhase.TRADING, 0.85f)
        "LOADING" in upperText -> OcrResult(DetectedPhase.LOADING, 0.85f)
        else                   -> OcrResult(DetectedPhase.UNKNOWN, 0f)
    }
}
