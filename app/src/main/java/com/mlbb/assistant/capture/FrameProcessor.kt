package com.mlbb.assistant.capture

import android.graphics.Bitmap
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

data class FrameAnalysis(
    val phase: PhaseDetector.DetectedPhase,
    val phaseConfidence: Float,
    val filledEnemyBanSlots: List<Int>,   // indices of newly filled slots
    val filledOurBanSlots: List<Int>,
    val filledEnemyPickSlots: List<Int>,
    val filledOurPickSlots: List<Int>,
    val banButtonVisible: Boolean
)

/**
 * Orchestrates per-frame analysis.
 *
 * Throttle constants are sourced from [PhaseDetectionConfig] (TD-03 / TD-04).
 * Slot-fill detection uses a normalised luminance threshold (TD-04): instead
 * of comparing mean luminance against an absolute value of 40, we compare
 * against a fraction of the *median* luminance of a calibration region so
 * adaptive-brightness and HDR displays do not cause false negatives.
 */
class FrameProcessor(
    private val portraitMatcher: PortraitMatcher,
    private val allHeroes: List<Hero>
) {

    private val _frameAnalysis = MutableSharedFlow<FrameAnalysis>(extraBufferCapacity = 4)
    val frameAnalysis: SharedFlow<FrameAnalysis> = _frameAnalysis.asSharedFlow()

    private var lastKnownPhase = PhaseDetector.DetectedPhase.UNKNOWN
    private var lastFrameTimeMs = 0L

    // Track which slots were already filled to emit only new fills.
    private val filledEnemyBans  = mutableSetOf<Int>()
    private val filledOurBans    = mutableSetOf<Int>()
    private val filledEnemyPicks = mutableSetOf<Int>()
    private val filledOurPicks   = mutableSetOf<Int>()

    // Normalised luminance baseline derived from a stable background region.
    // Lazily computed once per session — reset in [resetSlotTracking].
    private var lumBaseline: Float = -1f

    suspend fun processFrame(frame: Bitmap, currentPhase: DraftPhase) =
        withContext(Dispatchers.Default) {

            val now = System.currentTimeMillis()
            val throttleMs = if (currentPhase == DraftPhase.IDLE || currentPhase == DraftPhase.COMPLETE)
                PhaseDetectionConfig.CAPTURE_THROTTLE_IDLE_MS
            else
                PhaseDetectionConfig.CAPTURE_THROTTLE_ACTIVE_MS

            if (now - lastFrameTimeMs < throttleMs) return@withContext
            lastFrameTimeMs = now

            // Update luminance baseline from a stable region (top-left corner).
            if (lumBaseline < 0f) {
                lumBaseline = sampleLuminanceBaseline(frame)
            }

            // 1. Phase detection
            val phaseResult = PhaseDetector.detect(frame)
            lastKnownPhase = if (phaseResult.confidence > 0.5f) phaseResult.phase else lastKnownPhase

            // 2. Ban button detection (is it our turn?)
            val banButtonVisible = isBanButtonVisible(frame)

            // 3. Slot scanning (only during ban/pick phases)
            val newEnemyBans  = mutableListOf<Int>()
            val newOurBans    = mutableListOf<Int>()
            val newEnemyPicks = mutableListOf<Int>()
            val newOurPicks   = mutableListOf<Int>()

            if (currentPhase in listOf(DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2)) {
                scanBanSlots(frame, newEnemyBans, newOurBans)
            }
            if (currentPhase == DraftPhase.PICK) {
                scanPickSlots(frame, newEnemyPicks, newOurPicks)
            }

            _frameAnalysis.emit(FrameAnalysis(
                phase               = lastKnownPhase,
                phaseConfidence     = phaseResult.confidence,
                filledEnemyBanSlots = newEnemyBans,
                filledOurBanSlots   = newOurBans,
                filledEnemyPickSlots = newEnemyPicks,
                filledOurPickSlots  = newOurPicks,
                banButtonVisible    = banButtonVisible
            ))
        }

    fun resetSlotTracking() {
        filledEnemyBans.clear()
        filledOurBans.clear()
        filledEnemyPicks.clear()
        filledOurPicks.clear()
        lumBaseline = -1f
    }

    // ── Normalised luminance baseline (TD-04) ─────────────────────────────────

    /**
     * Samples mean luminance from the top-left 10 % × 10 % of the frame,
     * which is typically occupied by the MLBB game's stable dark background.
     * This baseline is used to normalise slot-fill detection against the raw
     * threshold, making the detector robust to adaptive-brightness displays.
     */
    private fun sampleLuminanceBaseline(frame: Bitmap): Float {
        val w = (frame.width * 0.10f).toInt().coerceAtLeast(4)
        val h = (frame.height * 0.10f).toInt().coerceAtLeast(4)
        var sum = 0f
        var count = 0
        val step = 2
        for (x in 0 until w step step) {
            for (y in 0 until h step step) {
                val px = frame.getPixel(x, y)
                val r  = android.graphics.Color.red(px)
                val g  = android.graphics.Color.green(px)
                val b  = android.graphics.Color.blue(px)
                sum += (0.299f * r + 0.587f * g + 0.114f * b)
                count++
            }
        }
        return if (count > 0) (sum / count) else PhaseDetectionConfig.LUMINANCE_DARK_THRESHOLD_RAW.toFloat()
    }

    // ── Slot detection ────────────────────────────────────────────────────────

    private fun isBanButtonVisible(frame: Bitmap): Boolean {
        val region = SlotRegions.actionButton
        val crop = SlotRegions.cropSlot(frame, region)
        val result = PhaseDetector.detect(crop)
        crop.recycle()
        return result.phase == PhaseDetector.DetectedPhase.BAN && result.confidence > 0.05f
    }

    private fun scanBanSlots(frame: Bitmap, newEnemy: MutableList<Int>, newOur: MutableList<Int>) {
        SlotRegions.enemyBanSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyBans && isSlotFilled(frame, region)) {
                filledEnemyBans.add(i)
                newEnemy.add(i)
            }
        }
        SlotRegions.ourBanSlots.forEachIndexed { i, region ->
            if (i !in filledOurBans && isSlotFilled(frame, region)) {
                filledOurBans.add(i)
                newOur.add(i)
            }
        }
    }

    private fun scanPickSlots(frame: Bitmap, newEnemy: MutableList<Int>, newOur: MutableList<Int>) {
        SlotRegions.enemyPickSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyPicks && isSlotFilled(frame, region)) {
                filledEnemyPicks.add(i)
                newEnemy.add(i)
            }
        }
        SlotRegions.ourPickSlots.forEachIndexed { i, region ->
            if (i !in filledOurPicks && isSlotFilled(frame, region)) {
                filledOurPicks.add(i)
                newOur.add(i)
            }
        }
    }

    /**
     * A slot is "filled" when its mean luminance exceeds a normalised threshold.
     *
     * TD-04: Instead of comparing against the hardcoded absolute value of 40,
     * we derive the threshold from [lumBaseline]:
     *
     *     threshold = max(baseline * LUMINANCE_NORMALISED_RATIO, LUMINANCE_DARK_THRESHOLD_RAW)
     *
     * This prevents HDR/auto-brightness displays from causing false negatives
     * where blank slots appear "filled" at elevated brightness levels.
     */
    private fun isSlotFilled(frame: Bitmap, region: SlotRegionF): Boolean {
        val crop = SlotRegions.cropSlot(frame, region)
        var total = 0f
        var count = 0
        val step = 4
        for (x in 0 until crop.width step step) {
            for (y in 0 until crop.height step step) {
                val px = crop.getPixel(x, y)
                val r  = android.graphics.Color.red(px)
                val g  = android.graphics.Color.green(px)
                val b  = android.graphics.Color.blue(px)
                total += (0.299f * r + 0.587f * g + 0.114f * b)
                count++
            }
        }
        crop.recycle()

        if (count == 0) return false

        val mean = total / count
        // Normalised threshold: at least the raw absolute, or baseline-relative (TD-04).
        val threshold = maxOf(
            PhaseDetectionConfig.LUMINANCE_DARK_THRESHOLD_RAW.toFloat(),
            lumBaseline * PhaseDetectionConfig.LUMINANCE_NORMALISED_RATIO
        )
        return mean > threshold
    }

    fun matchSlotPortrait(frame: Bitmap, region: SlotRegionF): MatchResult {
        val crop   = SlotRegions.cropSlot(frame, region)
        val result = portraitMatcher.match(crop, allHeroes)
        crop.recycle()
        return result
    }
}
