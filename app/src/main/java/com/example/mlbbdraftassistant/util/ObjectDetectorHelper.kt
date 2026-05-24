package com.example.mlbbdraftassistant.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * Wraps the TFLite YOLOv8 model for synchronous hero icon detection.
 *
 * FIX: replaced the callback-based [detect] with a synchronous [detectSync] return value
 * to avoid the forward-reference bug in [IconDetector] and to simplify the API.
 * Callers are responsible for dispatching to a background thread (Dispatchers.Default).
 */
class ObjectDetectorHelper(context: Context) {

    private var interpreter: Interpreter? = null
    private val labels: List<String>

    init {
        // Load labels
        labels = BufferedReader(InputStreamReader(context.assets.open(Constants.LABELS_PATH)))
            .useLines { it.toList() }

        // Load model
        val modelBuffer = FileUtil.loadMappedFile(context, Constants.MODEL_PATH)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelBuffer, options)

        Log.i("Detector", "Model loaded. Input: ${interpreter?.getInputTensor(0)?.shape()?.toList()}")
    }

    /**
     * Runs inference synchronously and returns the list of detections.
     * Must be called from a background thread.
     */
    fun detectSync(bitmap: Bitmap): List<Detection> {
        val interp = interpreter ?: return emptyList()
        val input = preprocess(bitmap)
        val output = Array(1) {
            FloatArray(Constants.NUM_DETECTIONS * (Constants.NUM_CLASSES + 4))
        }
        interp.run(input, output)
        return postprocess(output[0], bitmap.width, bitmap.height)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, Constants.INPUT_SIZE, Constants.INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(4 * Constants.INPUT_SIZE * Constants.INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(Constants.INPUT_SIZE * Constants.INPUT_SIZE)
        resized.getPixels(pixels, 0, Constants.INPUT_SIZE, 0, 0, Constants.INPUT_SIZE, Constants.INPUT_SIZE)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat(r / 255.0f)
            buffer.putFloat(g / 255.0f)
            buffer.putFloat(b / 255.0f)
        }
        return buffer
    }

    private fun postprocess(output: FloatArray, imgWidth: Int, imgHeight: Int): List<Detection> {
        val detections = mutableListOf<Detection>()

        val numClasses = Constants.NUM_CLASSES
        val numBoxes = Constants.NUM_DETECTIONS

        for (i in 0 until numBoxes) {
            var maxConf = 0f
            var maxClassId = -1

            for (c in 0 until numClasses) {
                val conf = sigmoid(output[c * numBoxes + i])
                if (conf > maxConf) {
                    maxConf = conf
                    maxClassId = c
                }
            }

            if (maxConf >= Constants.CONFIDENCE_THRESHOLD && maxClassId >= 0) {
                val cx = output[(numClasses + 0) * numBoxes + i]
                val cy = output[(numClasses + 1) * numBoxes + i]
                val w = output[(numClasses + 2) * numBoxes + i]
                val h = output[(numClasses + 3) * numBoxes + i]

                val left = (cx - w / 2f) * imgWidth / Constants.INPUT_SIZE
                val top = (cy - h / 2f) * imgHeight / Constants.INPUT_SIZE
                val right = (cx + w / 2f) * imgWidth / Constants.INPUT_SIZE
                val bottom = (cy + h / 2f) * imgHeight / Constants.INPUT_SIZE

                detections.add(
                    Detection(
                        classId = maxClassId,
                        label = if (maxClassId < labels.size) labels[maxClassId] else "Unknown",
                        confidence = maxConf,
                        boundingBox = BoundingBox(left, top, right, bottom)
                    )
                )
            }
        }

        return nonMaxSuppression(detections)
    }

    private fun nonMaxSuppression(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val keep = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!keep[i]) continue
            for (j in i + 1 until sorted.size) {
                if (!keep[j]) continue
                if (sorted[i].classId != sorted[j].classId) continue
                if (computeIoU(sorted[i].boundingBox, sorted[j].boundingBox) > Constants.IOU_THRESHOLD) {
                    keep[j] = false
                }
            }
        }

        return sorted.filterIndexed { index, _ -> keep[index] }
            .take(Constants.MAX_DETECTIONS)
    }

    private fun computeIoU(a: BoundingBox, b: BoundingBox): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        return interArea / (areaA + areaB - interArea + 1e-5f)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
