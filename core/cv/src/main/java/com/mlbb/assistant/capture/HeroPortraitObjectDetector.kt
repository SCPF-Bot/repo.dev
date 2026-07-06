package com.mlbb.assistant.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A single decoded YOLO detection, in **frame-normalised** coordinates (0..1
 * relative to the analysed [Bitmap]'s width/height — same convention as
 * [SlotRegionF]).
 *
 * @param label One of [HeroPortraitObjectDetector.CLASS_LABELS] — `"banned_hero"`,
 *   `"ally_hero"`, or `"enemy_hero"` (matches `scripts/train.py`'s `CLASSES` order,
 *   which is the source of truth — NOT the older list in `docs/plan.md`).
 */
data class DetectedPortrait(
    val boundingBox: RectF,
    val confidence: Float,
    val label: String,
)

/**
 * Hero-region object detector backed by the trained `mlbb_ui_detector.tflite`
 * YOLO model (see `scripts/train.py` / `.github/workflows/train.yml`).
 *
 * ### This class vs. [HeroClassifier]
 *
 * | Task | Class | Model | Status |
 * |---|---|---|---|
 * | **Detection** — *where* a hero-shaped region is, and which side/state it belongs to | [HeroPortraitObjectDetector] | YOLO, int8, 3 classes (`banned_hero`/`ally_hero`/`enemy_hero`), `mlbb_ui_detector.tflite` | ✅ Wired (Phase 2) |
 * | **Classification** — *which* hero is in an already-cropped slot | [HeroClassifier] | MobileNetV3Small softmax, `mlbb_hero_classifier.tflite` | ✅ Wired |
 *
 * This class only answers "is there a hero-shaped blob here, and what kind
 * (banned / our pick / enemy pick)?" — never *which* hero. That is still
 * [HeroClassifier] + [PortraitMatcher]'s job, run on the crop this class locates.
 *
 * ### Model I/O — read at runtime, not hardcoded
 *
 * `scripts/train.py` exports via `ultralytics .export(format='tflite', int8=True,
 * imgsz=416)`. Nothing in this repo can currently open the resulting `.tflite`
 * binary directly to confirm exact tensor shapes/dtypes/quantization params
 * (no Python/TF toolchain in this environment), so — deliberately — none of
 * that is hardcoded here. Instead:
 * - Input size, dtype, and quantization params are read from
 *   `interpreter.getInputTensor(0)` at load time.
 * - Output tensor shape/dtype/quantization params are read from
 *   `interpreter.getOutputTensor(0)` at load time, and the anchor/channel axis
 *   is inferred by checking which dimension equals `4 + CLASS_LABELS.size`
 *   (handles both `[1, 7, N]` and `[1, N, 7]` YOLOv8-style export layouts).
 * - Both tensors are read/written via raw [ByteBuffer] (not typed nested
 *   arrays), which lets [Interpreter.run] work regardless of the exact shape.
 *
 * If Phase 3+ testing on a real device reveals the raw box coordinates are in
 * a different unit than assumed (see the heuristic in [decodeOutput]), adjust
 * that one heuristic — the rest of the pipeline is shape-agnostic.
 *
 * ### Integration
 * [OverlayCaptureCoordinator] runs [detectPortraitRegions] once per captured
 * frame and feeds results through [TemporalConsensusBuffer] before treating a
 * slot as filled. When [isAvailable] is `false` (model missing/failed to load),
 * callers fall back entirely to the [SlotRegions] luminance/saturation heuristic.
 *
 * ### Thread safety
 * Mirrors [HeroClassifier]: [Interpreter] is not thread-safe, so all inference
 * is guarded by [detectLock].
 */
class HeroPortraitObjectDetector(context: Context) : Closeable {

    companion object {
        /** Must match the asset actually produced by `scripts/train.py` (fixed Phase 2 mismatch). */
        const val MODEL_ASSET_PATH = "mlbb_ui_detector.tflite"

        private const val NUM_THREADS = 2
        private const val TAG = "HeroPortraitOD"

        /** Class order MUST match `scripts/train.py`'s `CLASSES` list exactly. */
        val CLASS_LABELS = listOf("banned_hero", "ally_hero", "enemy_hero")

        /** If the max raw box coordinate is at or below this, treat outputs as already 0..1 normalised. */
        private const val NORMALISED_COORD_CEILING = 1.5f
    }

    private val interpreter: Interpreter?

    private var inputSize: Int = 416
    private var inputDataType: DataType = DataType.FLOAT32
    private var inputScale: Float = 1f
    private var inputZeroPoint: Int = 0

    private var outputDataType: DataType = DataType.FLOAT32
    private var outputScale: Float = 1f
    private var outputZeroPoint: Int = 0
    private var outputShape: IntArray = intArrayOf(1, 4 + CLASS_LABELS.size, 0)
    /** Index (1 or 2) of the output tensor's channel axis (the one sized `4 + numClasses`). */
    private var channelAxis: Int = 1

    val isAvailable: Boolean

    init {
        interpreter = runCatching {
            val buf = loadModelBuffer(context)
            val opts = Interpreter.Options().apply { setNumThreads(NUM_THREADS) }
            val interp = Interpreter(buf, opts)

            val inTensor = interp.getInputTensor(0)
            val inShape = inTensor.shape() // expected [1, H, W, 3]
            inputSize = inShape.getOrElse(1) { 416 }
            inputDataType = inTensor.dataType()
            val inQuant = inTensor.quantizationParams()
            inputScale = if (inQuant.scale != 0f) inQuant.scale else 1f
            inputZeroPoint = inQuant.zeroPoint

            val outTensor = interp.getOutputTensor(0)
            outputShape = outTensor.shape()
            outputDataType = outTensor.dataType()
            val outQuant = outTensor.quantizationParams()
            outputScale = if (outQuant.scale != 0f) outQuant.scale else 1f
            outputZeroPoint = outQuant.zeroPoint

            val expectedChannels = 4 + CLASS_LABELS.size
            channelAxis = when (expectedChannels) {
                outputShape.getOrNull(1) -> 1
                outputShape.getOrNull(2) -> 2
                else -> {
                    Timber.w(
                        "$TAG: unexpected output shape ${outputShape.toList()} — " +
                            "neither axis matches 4+${CLASS_LABELS.size} classes; defaulting to axis 1"
                    )
                    1
                }
            }

            interp
        }.onFailure { e ->
            Timber.w(e, "$TAG: model load failed ('$MODEL_ASSET_PATH') — YOLO detector disabled, falling back to SlotRegions")
        }.getOrNull()

        isAvailable = interpreter != null
        Timber.i(
            "$TAG: init — isAvailable=$isAvailable inputSize=$inputSize " +
                "inputType=$inputDataType outputShape=${outputShape.toList()} channelAxis=$channelAxis"
        )
    }

    /** [Interpreter] is not thread-safe — see [HeroClassifier]'s equivalent lock. */
    private val detectLock = Any()

    /**
     * Runs YOLO detection on the full [frame] and returns decoded, NMS-filtered
     * boxes in frame-normalised coordinates.
     *
     * Returns an empty list when [isAvailable] is `false` or inference fails —
     * callers must treat that as "no signal", not "confidently empty", and fall
     * back to [SlotRegions]-based detection.
     */
    fun detectPortraitRegions(
        frame: Bitmap,
        confidenceThreshold: Float = PhaseDetectionConfig.YOLO_CONFIDENCE_THRESHOLD,
        iouThreshold: Float = PhaseDetectionConfig.YOLO_NMS_IOU_THRESHOLD,
    ): List<DetectedPortrait> = synchronized(detectLock) {
        val interp = interpreter ?: return@synchronized emptyList()
        if (outputShape.size < 3) return@synchronized emptyList()

        runCatching {
            val resized = Bitmap.createScaledBitmap(frame, inputSize, inputSize, true)
            val inputBuffer = preprocess(resized)
            if (resized !== frame) resized.recycle()

            val outTensor = interp.getOutputTensor(0)
            val outputBuffer = ByteBuffer.allocateDirect(outTensor.numBytes())
            outputBuffer.order(ByteOrder.nativeOrder())

            interp.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            decodeOutput(outputBuffer, confidenceThreshold, iouThreshold)
        }.onFailure { e ->
            Timber.w(e, "$TAG: inference failed — no detections this frame")
        }.getOrDefault(emptyList())
    }

    override fun close() {
        interpreter?.close()
    }

    // ── Preprocessing ──────────────────────────────────────────────────────

    /** Builds the raw input [ByteBuffer], quantising per-channel if the model expects int8/uint8. */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val quantized = inputDataType == DataType.UINT8 || inputDataType == DataType.INT8
        val bytesPerChannel = if (quantized) 1 else 4
        val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * bytesPerChannel)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (quantized) {
                buffer.put(quantizeChannel(r / 255f))
                buffer.put(quantizeChannel(g / 255f))
                buffer.put(quantizeChannel(b / 255f))
            } else {
                // Standard ultralytics preprocessing: 0..1 float, no mean subtraction.
                buffer.putFloat(r / 255f)
                buffer.putFloat(g / 255f)
                buffer.putFloat(b / 255f)
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun quantizeChannel(normalized: Float): Byte {
        val raw = (normalized / inputScale + inputZeroPoint).roundToInt()
        return if (inputDataType == DataType.INT8) {
            raw.coerceIn(-128, 127).toByte()
        } else {
            raw.coerceIn(0, 255).toByte()
        }
    }

    // ── Output decoding ────────────────────────────────────────────────────

    private data class RawBox(val cx: Float, val cy: Float, val w: Float, val h: Float, val classId: Int, val score: Float)

    /**
     * Decodes the raw YOLO output tensor into [DetectedPortrait]s.
     *
     * Handles both `[1, 4+numClasses, numAnchors]` (channels-first, common for
     * newer ultralytics TFLite exports) and `[1, numAnchors, 4+numClasses]`
     * (channels-last) layouts generically via [channelAxis], determined at
     * model-load time.
     *
     * Box coordinate unit heuristic: ultralytics TFLite export may emit box
     * centre/size either already normalised to 0..1, or in pixel units of the
     * network's input size (0..[inputSize]). We scan all kept boxes' raw
     * coordinates first; if the max exceeds [NORMALISED_COORD_CEILING], we treat
     * them as pixel-space and divide by [inputSize].
     */
    private fun decodeOutput(
        buffer: ByteBuffer,
        confidenceThreshold: Float,
        iouThreshold: Float,
    ): List<DetectedPortrait> {
        val numAnchors = if (channelAxis == 1) outputShape.getOrElse(2) { 0 } else outputShape.getOrElse(1) { 0 }
        val numChannels = 4 + CLASS_LABELS.size
        if (numAnchors <= 0) return emptyList()

        val quantized = outputDataType == DataType.UINT8 || outputDataType == DataType.INT8
        val floatBuf = if (!quantized) buffer.asFloatBuffer() else null

        fun rawValueAt(flatIndex: Int): Float {
            return if (quantized) {
                val raw: Int = if (outputDataType == DataType.INT8) {
                    buffer.get(flatIndex).toInt()
                } else {
                    buffer.get(flatIndex).toInt() and 0xFF
                }
                (raw - outputZeroPoint) * outputScale
            } else {
                floatBuf!!.get(flatIndex)
            }
        }

        // flat index for (channel c, anchor n) given the detected layout.
        fun valueAt(c: Int, n: Int): Float {
            val flat = if (channelAxis == 1) c * numAnchors + n else n * numChannels + c
            return rawValueAt(flat)
        }

        val candidates = ArrayList<RawBox>()
        for (n in 0 until numAnchors) {
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until CLASS_LABELS.size) {
                val s = valueAt(4 + c, n)
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestClass >= 0 && bestScore >= confidenceThreshold) {
                val cx = valueAt(0, n)
                val cy = valueAt(1, n)
                val w = valueAt(2, n)
                val h = valueAt(3, n)
                candidates.add(RawBox(cx, cy, w, h, bestClass, bestScore))
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val maxCoord = candidates.maxOf { max(max(it.cx, it.cy), max(it.w, it.h)) }
        val normFactor = if (maxCoord <= NORMALISED_COORD_CEILING) 1f else inputSize.toFloat()

        val boxes = candidates.map { box ->
            val cx = box.cx / normFactor
            val cy = box.cy / normFactor
            val w = box.w / normFactor
            val h = box.h / normFactor
            DetectedPortrait(
                boundingBox = RectF(
                    (cx - w / 2f).coerceIn(0f, 1f),
                    (cy - h / 2f).coerceIn(0f, 1f),
                    (cx + w / 2f).coerceIn(0f, 1f),
                    (cy + h / 2f).coerceIn(0f, 1f),
                ),
                confidence = box.score,
                label = CLASS_LABELS[box.classId],
            )
        }

        return nonMaxSuppression(boxes, iouThreshold)
    }

    /** Greedy NMS, grouped by class label so overlapping banned/ally/enemy boxes never suppress each other. */
    private fun nonMaxSuppression(boxes: List<DetectedPortrait>, iouThreshold: Float): List<DetectedPortrait> {
        val result = ArrayList<DetectedPortrait>()
        for ((_, group) in boxes.groupBy { it.label }) {
            val remaining = group.sortedByDescending { it.confidence }.toMutableList()
            while (remaining.isNotEmpty()) {
                val best = remaining.removeAt(0)
                result.add(best)
                remaining.removeAll { iou(it.boundingBox, best.boundingBox) > iouThreshold }
            }
        }
        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val interW = (right - left).coerceAtLeast(0f)
        val interH = (bottom - top).coerceAtLeast(0f)
        val interArea = interW * interH
        val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
        val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
        val union = areaA + areaB - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    /**
     * Memory-maps the TFLite model from assets — see [HeroClassifier.loadModelBuffer]
     * for why memory-mapping (vs. reading into a heap array) is required.
     */
    private fun loadModelBuffer(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_ASSET_PATH)
        return FileInputStream(fd.fileDescriptor).use { fis ->
            fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
}
