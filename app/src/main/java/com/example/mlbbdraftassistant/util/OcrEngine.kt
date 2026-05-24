package com.example.mlbbdraftassistant.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OcrEngine {

    // FIX: lazy-initialize so the recognizer is only created on first use,
    // not at class-load time (which happens even in non-OCR modes).
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs ML Kit OCR on [bitmap] and returns the recognised text, or null on failure.
     *
     * FIX: the crop bitmaps produced by [CropRegions.cropBitmap] may occasionally
     * be empty (0×0 px) when a region falls outside the screen bounds.
     * Passing a zero-size bitmap to ML Kit throws an IllegalArgumentException.
     * We guard against this here instead of crashing.
     */
    suspend fun recognize(bitmap: Bitmap): String? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text.trim().ifBlank { null })
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }
}
