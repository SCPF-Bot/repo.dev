package com.example.mlbbdraftassistant.util

object Constants {
    const val MODEL_PATH = "hero_detector_int8.tflite"
    const val LABELS_PATH = "labels.txt"

    const val INPUT_SIZE = 640
    const val NUM_DETECTIONS = 8400   // 80x80 + 40x40 + 20x20
    const val NUM_CLASSES = 126       // All MLBB heroes + "Unknown"

    const val CONFIDENCE_THRESHOLD = 0.6f
    const val IOU_THRESHOLD = 0.5f
    const val MAX_DETECTIONS = 15
}

data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox
)

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}