package com.mlbb.assistant.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mlbb.assistant.capture.PhaseDetector.DetectedPhase

/**
 * Section 3.2.4 — Phase disambiguation via ML Kit On-Device OCR.
 *
 * [PhaseDetector] uses colour histogram heuristics which can misclassify
 * edge-case frames (e.g. dark loading screens vs. ban phase).
 * [PhaseOcrDetector] reads visible text labels ("BAN", "PICK", "READY")
 * to produce a higher-confidence classification.
 *
 * Dependency: `com.google.mlkit:text-recognition:16.0.1` (declared in libs.versions.toml).
 * Previously the dependency was loaded reflectively to avoid a hard compile-time
 * dependency. Now that the artifact is declared explicitly, direct ML Kit imports
 * are used for type safety, clarity, and to eliminate the reflection overhead.
 *
 * The OCR scan is intentionally restricted to the top 20 % of the frame
 * where MLBB prints phase labels, keeping latency low (~30 ms on mid-range).
 */
object PhaseOcrDetector {

    data class OcrResult(val phase: DetectedPhase, val confidence: Float)

    /**
     * Region (normalised) containing the phase label text.
     * MLBB prints "BAN PHASE", "PICK PHASE", "TRADING" etc. in this band.
     */
    private val TEXT_REGION = SlotRegionF(left = 0.25f, top = 0.00f, right = 0.75f, bottom = 0.18f)

    // Lazy singleton recognizer — ML Kit initialises its model on first use.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Attempts to read the phase label from [frame] using ML Kit Text Recognition.
     *
     * [onResult] is invoked asynchronously on the calling thread's Looper once
     * recognition completes. Returns [OcrResult] with [DetectedPhase.UNKNOWN]
     * and confidence 0 when:
     *   - The crop region is empty or fails
     *   - ML Kit recognition fails
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

        val image = runCatching { InputImage.fromBitmap(crop, 0) }.getOrNull()
        if (image == null) {
            crop.recycle()
            onResult(OcrResult(DetectedPhase.UNKNOWN, 0f))
            return
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                crop.recycle()
                val text = visionText.text.uppercase()
                onResult(classifyText(text))
            }
            .addOnFailureListener {
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
