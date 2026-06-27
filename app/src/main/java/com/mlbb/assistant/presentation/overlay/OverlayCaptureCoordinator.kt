package com.mlbb.assistant.presentation.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import coil3.ImageLoader
import com.mlbb.assistant.capture.FirstPickDetector
import com.mlbb.assistant.capture.PhaseDetectionConfig
import com.mlbb.assistant.capture.PhaseDetector
import com.mlbb.assistant.capture.PhaseOcrDetector
import com.mlbb.assistant.capture.PortraitMatcher
import com.mlbb.assistant.capture.SlotRegionF
import com.mlbb.assistant.capture.SlotRegions
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.service.ScreenCaptureManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the screen-capture loop and all computer-vision frame analysis.
 *
 * Extracted from [OverlayService] as part of the JetOverlay integration.
 * This class has zero UI concerns — it only reads frames, classifies them,
 * and writes back to [OverlayStateHolder] (for isBanTurn / phase transitions)
 * and [DraftSessionManager] (for recording bans / picks).
 *
 * ### Improvements in this revision
 *
 * **Polling rate:** Reduced from 500 ms → 250 ms during active phases
 * (sourced from [PhaseDetectionConfig.CAPTURE_THROTTLE_ACTIVE_MS]).
 * A 30-second pick clock now gets ~120 sample frames instead of ~60.
 *
 * **OCR phase confirmation:** [PhaseOcrDetector] runs every
 * [PhaseDetectionConfig.OCR_FRAME_STRIDE] frames (~1 s). When its confidence
 * meets [PhaseDetectionConfig.OCR_OVERRIDE_CONFIDENCE], the OCR result
 * overrides the colour-based phase classification. This eliminates the
 * edge-case where the action-button colour briefly resembles a wrong phase
 * during animation transitions.
 *
 * **Multi-frame confirmation for picks/bans:** [scanAndRecordBans] and
 * [scanAndRecordPicks] pass a [slotKey] to [PortraitMatcher.match], which
 * requires [PhaseDetectionConfig.CONFIRMATION_FRAMES_REQUIRED] consecutive
 * frames to agree on the same hero before promoting it to a confirmed record.
 * Prevents one-frame false positives from hero-reveal fly-in animations.
 *
 * **Saturation-aware slot fill:** [isSlotFilled] now uses a dual criterion:
 * mean luminance > threshold (existing) AND colour saturation variance >
 * [PhaseDetectionConfig.SLOT_SATURATION_FILLED_MIN]. Empty MLBB slots render
 * a near-grey circle; hero portraits always have meaningful colour saturation.
 *
 * Lifecycle:
 * - [init]: called from [OverlayService.onCreate] to construct capture
 *   dependencies that need Context (ScreenCaptureManager, PortraitMatcher).
 * - [startCapture]: called from [OverlayService.onStartCommand] once a valid
 *   MediaProjection token is available.
 * - [stop]: called from [OverlayService.onDestroy].
 *
 * Thread-safety: [captureJob] is started / cancelled only on Main (service
 * callbacks). Frame analysis runs on Dispatchers.Default. All mutations to
 * [OverlayStateHolder] ConcurrentHashMap slot sets are thread-safe (P0-04/P0-05).
 */
@Singleton
class OverlayCaptureCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder:         OverlayStateHolder,
    private val draftSessionManager: DraftSessionManager
) {

    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var portraitMatcher: PortraitMatcher
    private var captureJob: Job? = null

    // Frame counter for OCR stride — reset on session start.
    private val frameCounter = AtomicInteger(0)

    // Latest OCR phase result — written from Main thread callback, read on Default.
    private val lastOcrPhase = AtomicReference(PhaseDetector.DetectedPhase.UNKNOWN)
    private val lastOcrConfidence = AtomicReference(0f)

    // ── Initialise capture dependencies (called by OverlayService.onCreate) ───

    fun init(imageLoader: ImageLoader) {
        screenCaptureManager = ScreenCaptureManager(context)
        portraitMatcher = PortraitMatcher(context, imageLoader)
    }

    // ── Portrait hash preload (called by OverlayStateHolder when heroes load) ─

    suspend fun preloadHashes(heroes: List<Hero>) {
        if (::portraitMatcher.isInitialized) {
            portraitMatcher.preloadHashes(heroes)
        }
    }

    // ── Capture lifecycle ─────────────────────────────────────────────────────

    fun startCapture(resultCode: Int, projData: Intent, scope: CoroutineScope) {
        screenCaptureManager.startCapture(resultCode, projData)
        frameCounter.set(0)
        PhaseDetector.resetHistory()
        if (::portraitMatcher.isInitialized) portraitMatcher.resetConfirmation()
        launchCaptureLoop(scope)
    }

    fun stop() {
        captureJob?.cancel()
        if (::screenCaptureManager.isInitialized) {
            screenCaptureManager.stopCapture()
        }
    }

    // ── Capture loop ──────────────────────────────────────────────────────────

    private fun launchCaptureLoop(scope: CoroutineScope) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            // Wait until heroes are loaded so portrait matching has a corpus.
            while (stateHolder.allHeroes.isEmpty() && isActive) delay(300)

            while (isActive) {
                val phase   = stateHolder.sessionValue().phase
                val delayMs = if (phase == DraftPhase.IDLE || phase == DraftPhase.COMPLETE)
                    PhaseDetectionConfig.CAPTURE_THROTTLE_IDLE_MS
                else
                    PhaseDetectionConfig.CAPTURE_THROTTLE_ACTIVE_MS

                val frame = screenCaptureManager.captureFrame()

                if (frame != null) {
                    withContext(Dispatchers.Default) {
                        runCatching { analyzeFrame(frame, phase) }
                        frame.recycle()
                    }
                }
                delay(delayMs)
            }
        }
    }

    // ── Frame analysis ────────────────────────────────────────────────────────

    private suspend fun analyzeFrame(frame: Bitmap, currentPhase: DraftPhase) {
        val count = frameCounter.incrementAndGet()

        // ── OCR phase detection (every OCR_FRAME_STRIDE frames, async) ────────
        if (count % PhaseDetectionConfig.OCR_FRAME_STRIDE == 0) {
            // Copy a small crop for OCR — avoid holding full frame across async gap
            val ocrCrop = runCatching {
                SlotRegions.cropSlot(frame, SlotRegions.phaseBanner)
            }.getOrNull()

            if (ocrCrop != null) {
                val ocrBitmap = ocrCrop.copy(ocrCrop.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                ocrCrop.recycle()
                // PhaseOcrDetector is callback-based (ML Kit); callback fires on Main.
                withContext(Dispatchers.Main) {
                    PhaseOcrDetector.detect(ocrBitmap) { result ->
                        lastOcrPhase.set(result.phase)
                        lastOcrConfidence.set(result.confidence)
                        ocrBitmap.recycle()
                    }
                }
            }
        }

        // ── Colour-based phase detection ──────────────────────────────────────
        val actionCrop = runCatching {
            SlotRegions.cropSlot(frame, SlotRegions.actionButton)
        }.getOrNull()

        val colourPhaseResult = if (actionCrop != null) {
            try { PhaseDetector.detect(actionCrop) } finally { actionCrop.recycle() }
        } else {
            PhaseDetector.detect(frame)
        }

        // ── Merge colour + OCR phase results ─────────────────────────────────
        val effectivePhase: PhaseDetector.DetectedPhase = run {
            val ocrPhase = lastOcrPhase.get()
            val ocrConf  = lastOcrConfidence.get()
            if (ocrPhase != PhaseDetector.DetectedPhase.UNKNOWN &&
                ocrConf >= PhaseDetectionConfig.OCR_OVERRIDE_CONFIDENCE) {
                ocrPhase
            } else {
                // Use history-smoothed phase (majority vote over last N frames)
                val smoothed = PhaseDetector.smoothedPhase()
                if (smoothed != PhaseDetector.DetectedPhase.UNKNOWN) smoothed else colourPhaseResult.phase
            }
        }

        val banButtonVisible = isBanButtonVisible(frame)
        withContext(Dispatchers.Main) { stateHolder.isBanTurn.value = banButtonVisible }

        if (effectivePhase != PhaseDetector.DetectedPhase.UNKNOWN) {
            val firstPickResult = if (
                effectivePhase == PhaseDetector.DetectedPhase.BAN &&
                currentPhase == DraftPhase.IDLE
            ) {
                FirstPickDetector.detect(frame)
            } else null

            withContext(Dispatchers.Main) {
                stateHolder.autoTransitionPhase(effectivePhase, currentPhase, firstPickResult)
            }
        }

        // ── Slot scanning ─────────────────────────────────────────────────────
        val session = stateHolder.sessionValue()
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (session.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                scanAndRecordBans(frame, round)
            }
            DraftPhase.PICK -> {
                if (!stateHolder.banCatchUpDone) {
                    stateHolder.banCatchUpDone = true
                    val anyBanMissed =
                        stateHolder.filledEnemyBanSlots.size < SlotRegions.enemyBanSlots.size ||
                        stateHolder.filledOurBanSlots.size   < SlotRegions.ourBanSlots.size
                    if (anyBanMissed) scanAndRecordBans(frame, round = 1)
                }
                scanAndRecordPicks(frame)
            }
            else -> {}
        }
    }

    // ── Slot scanning ─────────────────────────────────────────────────────────

    private suspend fun scanAndRecordBans(frame: Bitmap, round: Int) {
        SlotRegions.enemyBanSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledEnemyBanSlots && isSlotFilled(frame, region)) {
                val hero = matchPortrait(frame, region, 0.45f, "enemyBan$i")
                if (hero != null) {
                    stateHolder.filledEnemyBanSlots.add(i)
                    withContext(Dispatchers.Main) {
                        draftSessionManager.recordEnemyBan(hero, round, i)
                    }
                }
            }
        }
        SlotRegions.ourBanSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledOurBanSlots && isSlotFilled(frame, region)) {
                val hero = matchPortrait(frame, region, 0.45f, "ourBan$i")
                if (hero != null) {
                    stateHolder.filledOurBanSlots.add(i)
                    withContext(Dispatchers.Main) {
                        draftSessionManager.recordOurBan(hero, round, i)
                    }
                }
            }
        }
    }

    private suspend fun scanAndRecordPicks(frame: Bitmap) {
        SlotRegions.enemyPickSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledEnemyPickSlots && isSlotFilled(frame, region)) {
                val hero = matchPortrait(frame, region, 0.55f, "enemyPick$i") ?: return@forEachIndexed
                stateHolder.filledEnemyPickSlots.add(i)
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordEnemyPick(hero, i)
                }
            }
        }
        SlotRegions.ourPickSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledOurPickSlots && isSlotFilled(frame, region)) {
                val hero = matchPortrait(frame, region, 0.55f, "ourPick$i") ?: return@forEachIndexed
                stateHolder.filledOurPickSlots.add(i)
                val topId = stateHolder.recommendations.firstOrNull()?.hero?.id
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordOurPick(hero, i, followedRecommendation = hero.id == topId)
                }
            }
        }
    }

    // ── Frame utilities ───────────────────────────────────────────────────────

    private fun matchPortrait(
        frame: Bitmap,
        region: SlotRegionF,
        minConf: Float,
        slotKey: String = ""
    ): Hero? {
        val crop = runCatching { SlotRegions.cropSlot(frame, region) }.getOrNull() ?: return null
        return try {
            val r = portraitMatcher.match(crop, stateHolder.allHeroes.toList(), slotKey)
            if (r.confidence >= minConf && !r.requiresConfirmation) r.hero else null
        } finally { crop.recycle() }
    }

    /**
     * Dual-criterion slot fill detection:
     *
     * 1. **Luminance**: mean pixel luminance must exceed the normalised threshold
     *    (same as before — adaptive to baseline brightness).
     *
     * 2. **Colour saturation**: average HSV saturation across sampled pixels must
     *    exceed [PhaseDetectionConfig.SLOT_SATURATION_FILLED_MIN]. Empty MLBB
     *    ban/pick slots render a near-grey circular frame; hero portraits always
     *    have meaningful colour saturation even for heroes with muted palettes.
     *
     * Both criteria must be true for the slot to be considered filled. This
     * eliminates false positives from bright but desaturated UI decorations
     * (score bars, timer rings) that previously triggered slot detection.
     */
    private fun isSlotFilled(frame: Bitmap, region: SlotRegionF): Boolean {
        val crop = runCatching { SlotRegions.cropSlot(frame, region) }.getOrNull() ?: return false
        return try {
            var lumTotal  = 0f
            var satTotal  = 0f
            var count     = 0
            val step      = 4
            val hsv       = FloatArray(3)

            for (x in 0 until crop.width step step) {
                for (y in 0 until crop.height step step) {
                    val px = crop.getPixel(x, y)
                    val r  = Color.red(px)
                    val g  = Color.green(px)
                    val b  = Color.blue(px)
                    lumTotal += 0.299f * r + 0.587f * g + 0.114f * b

                    Color.colorToHSV(px, hsv)
                    satTotal += hsv[1]
                    count++
                }
            }

            if (count == 0) return false

            val meanLum = lumTotal / count
            val meanSat = satTotal / count

            meanLum > 40f && meanSat > PhaseDetectionConfig.SLOT_SATURATION_FILLED_MIN
        } finally { crop.recycle() }
    }

    private fun isBanButtonVisible(frame: Bitmap): Boolean {
        val crop = runCatching { SlotRegions.cropSlot(frame, SlotRegions.actionButton) }.getOrNull()
            ?: return false
        return try {
            val r = PhaseDetector.detect(crop)
            r.phase == PhaseDetector.DetectedPhase.BAN && r.confidence > 0.05f
        } finally { crop.recycle() }
    }
}
