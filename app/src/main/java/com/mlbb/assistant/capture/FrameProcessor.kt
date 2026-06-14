package com.mlbb.assistant.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
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
 * Throttled to 2fps during active draft phases (performance).
 * Falls back to 0.5fps when phase is IDLE or COMPLETE.
 */
class FrameProcessor(
    private val portraitMatcher: PortraitMatcher,
    private val allHeroes: List<Hero>
) {

    private val _frameAnalysis = MutableSharedFlow<FrameAnalysis>(extraBufferCapacity = 4)
    val frameAnalysis: SharedFlow<FrameAnalysis> = _frameAnalysis.asSharedFlow()

    private var lastKnownPhase = PhaseDetector.DetectedPhase.UNKNOWN
    private var lastFrameTimeMs = 0L

    // Track which slots were already filled to emit only new fills
    private val filledEnemyBans = mutableSetOf<Int>()
    private val filledOurBans   = mutableSetOf<Int>()
    private val filledEnemyPicks = mutableSetOf<Int>()
    private val filledOurPicks   = mutableSetOf<Int>()

    suspend fun processFrame(frame: Bitmap, currentPhase: DraftPhase) =
        withContext(Dispatchers.Default) {

            val now = System.currentTimeMillis()
            val throttleMs = if (currentPhase == DraftPhase.IDLE || currentPhase == DraftPhase.COMPLETE) 2000L else 500L
            if (now - lastFrameTimeMs < throttleMs) return@withContext
            lastFrameTimeMs = now

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
    }

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
     * A slot is considered "filled" if its dominant color is not
     * the dark draft background (mean luminance > 40).
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
        return count > 0 && (total / count) > 40f
    }

    fun matchSlotPortrait(frame: Bitmap, region: SlotRegionF): MatchResult {
        val crop   = SlotRegions.cropSlot(frame, region)
        val result = portraitMatcher.match(crop, allHeroes)
        crop.recycle()
        return result
    }
}
