package com.mlbb.assistant.capture

import android.graphics.Bitmap
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

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
 *
 * P1-01 fix: [sampleLuminanceBaseline] and [isSlotFilled] now use
 * [Bitmap.copyPixelsToBuffer] + buffer-array iteration instead of
 * repeated [Bitmap.getPixel] JNI calls. The previous implementation made
 * one JNI call per sampled pixel; the new implementation makes a single bulk
 * copy into a [ByteBuffer] and iterates the backing byte array in pure Kotlin.
 * Expected improvement: 5–20× on mid-range devices.
 *
 * P0-05 fix: The four internal slot-tracking sets are read and mutated from
 * [processFrame] running on [Dispatchers.Default], while [resetSlotTracking]
 * can be called from [com.mlbb.assistant.presentation.overlay.OverlayService]
 * on a different dispatcher. Plain [mutableSetOf] is NOT thread-safe under
 * that access pattern. All four sets are now [ConcurrentHashMap.newKeySet]
 * — lock-free, thread-safe [MutableSet] with identical semantics.
 */
class FrameProcessor(
    private val portraitMatcher: PortraitMatcher,
    private val allHeroes: List<Hero>
) {

    private val _frameAnalysis = MutableSharedFlow<FrameAnalysis>(extraBufferCapacity = 4)
    val frameAnalysis: SharedFlow<FrameAnalysis> = _frameAnalysis.asSharedFlow()

    private var lastKnownPhase = PhaseDetector.DetectedPhase.UNKNOWN
    private var lastFrameTimeMs = 0L

    // P0-05: These sets are mutated inside withContext(Dispatchers.Default) in
    // processFrame, while resetSlotTracking() is called from OverlayService which
    // may run on Main or IO. A plain mutableSetOf is NOT thread-safe across
    // dispatcher boundaries. ConcurrentHashMap.newKeySet() provides lock-free
    // thread-safety with the same add/contains/in/clear/size semantics.
    private val filledEnemyBans:  MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val filledOurBans:    MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val filledEnemyPicks: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val filledOurPicks:   MutableSet<Int> = ConcurrentHashMap.newKeySet()

    // Normalised luminance baseline derived from a stable background region.
    // Lazily computed once per session — reset in [resetSlotTracking].
    private var lumBaseline: Float = -1f

    /**
     * Analyses a single captured frame.
     *
     * @param frame         The bitmap to analyse (recycled by caller after return).
     * @param currentPhase  The current draft phase from [DraftSessionManager].
     * @param banDraftType  The active ban layout for the current rank tier.
     *                      Determines which ban slot regions are scanned — only
     *                      the slots that are rendered on screen for this rank are
     *                      checked, preventing false-positive detections on invisible
     *                      slot positions. Defaults to [BanDraftType.EPIC_6_BANS].
     *                      Pass the result of [BanDraftType.fromRank] whenever the
     *                      session rank is known.
     */
    suspend fun processFrame(
        frame: Bitmap,
        currentPhase: DraftPhase,
        banDraftType: BanDraftType = BanDraftType.EPIC_6_BANS
    ) = withContext(Dispatchers.Default) {

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
                val template = BanSlotTemplates.forDraftType(banDraftType)
                scanBanSlots(frame, newEnemyBans, newOurBans, template)
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

    // ── Normalised luminance baseline (TD-04, P1-01) ──────────────────────────

    /**
     * Samples mean luminance from the top-left 10 % × 10 % of the frame,
     * which is typically occupied by the MLBB game's stable dark background.
     * This baseline is used to normalise slot-fill detection against the raw
     * threshold, making the detector robust to adaptive-brightness displays.
     *
     * P1-01: Uses [Bitmap.copyPixelsToBuffer] for bulk pixel extraction,
     * then iterates the backing byte array. Avoids one JNI call per pixel.
     * Sampling pattern: every 2nd pixel in x and every 2nd row in y
     * (identical density to the previous [Bitmap.getPixel] implementation).
     */
    private fun sampleLuminanceBaseline(frame: Bitmap): Float {
        val w = (frame.width  * 0.10f).toInt().coerceAtLeast(4)
        val h = (frame.height * 0.10f).toInt().coerceAtLeast(4)
        val crop = Bitmap.createBitmap(frame, 0, 0, w, h)
        val bytes = ByteBuffer.allocate(crop.byteCount)
        crop.copyPixelsToBuffer(bytes)
        crop.recycle()

        val arr      = bytes.array()
        val rowBytes = w * 4   // ARGB_8888: 4 bytes per pixel
        val step     = 2       // sample every 2nd pixel in x; every 2nd row in y
        var sum      = 0f
        var count    = 0
        var row      = 0
        while (row < h) {
            var col = 0
            while (col < w) {
                val i = row * rowBytes + col * 4
                val r = arr[i    ].toInt() and 0xFF
                val g = arr[i + 1].toInt() and 0xFF
                val b = arr[i + 2].toInt() and 0xFF
                sum   += (0.299f * r + 0.587f * g + 0.114f * b)
                count++
                col   += step
            }
            row += step
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

    /**
     * Scans only the ban slots that are active for the current rank tier.
     *
     * [template] is resolved from [BanSlotTemplates.forDraftType] and contains
     * exactly the slots rendered on screen for the session's rank. Scanning beyond
     * [template.draftType.slotsPerTeam] slots risks false-positive detections on
     * screen regions that are not ban slots at the current rank.
     */
    private fun scanBanSlots(
        frame: Bitmap,
        newEnemy: MutableList<Int>,
        newOur: MutableList<Int>,
        template: BanSlotTemplate
    ) {
        template.enemyBanSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyBans && isSlotFilled(frame, region)) {
                filledEnemyBans.add(i)
                newEnemy.add(i)
            }
        }
        template.ourBanSlots.forEachIndexed { i, region ->
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
     *
     * P1-01: Uses [Bitmap.copyPixelsToBuffer] bulk extraction instead of
     * per-pixel [Bitmap.getPixel] JNI calls. Sampling density is identical:
     * every 4th pixel in x and every 4th row in y.
     */
    private fun isSlotFilled(frame: Bitmap, region: SlotRegionF): Boolean {
        val crop  = SlotRegions.cropSlot(frame, region)
        val cropW = crop.width
        val cropH = crop.height
        val bytes = ByteBuffer.allocate(crop.byteCount)
        crop.copyPixelsToBuffer(bytes)
        crop.recycle()

        val arr      = bytes.array()
        val rowBytes = cropW * 4   // ARGB_8888: 4 bytes per pixel
        val step     = 4           // sample every 4th pixel in x; every 4th row in y
        var total    = 0f
        var count    = 0
        var row      = 0
        while (row < cropH) {
            var col = 0
            while (col < cropW) {
                val i = row * rowBytes + col * 4
                val r = arr[i    ].toInt() and 0xFF
                val g = arr[i + 1].toInt() and 0xFF
                val b = arr[i + 2].toInt() and 0xFF
                total += (0.299f * r + 0.587f * g + 0.114f * b)
                count++
                col += step
            }
            row += step
        }

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
