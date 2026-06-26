package com.mlbb.assistant.presentation.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import coil3.ImageLoader
import com.mlbb.assistant.capture.FirstPickDetector
import com.mlbb.assistant.capture.PhaseDetector
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
                val delayMs = if (phase == DraftPhase.IDLE || phase == DraftPhase.COMPLETE) 2_000L else 500L
                val frame   = screenCaptureManager.captureFrame()

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
        val actionCrop = runCatching {
            SlotRegions.cropSlot(frame, SlotRegions.actionButton)
        }.getOrNull()

        val phaseResult = if (actionCrop != null) {
            try { PhaseDetector.detect(actionCrop) } finally { actionCrop.recycle() }
        } else {
            PhaseDetector.detect(frame)
        }

        val banButtonVisible = isBanButtonVisible(frame)
        withContext(Dispatchers.Main) { stateHolder.isBanTurn.value = banButtonVisible }

        if (phaseResult.phase != PhaseDetector.DetectedPhase.UNKNOWN) {
            val firstPickResult = if (
                phaseResult.phase == PhaseDetector.DetectedPhase.BAN &&
                currentPhase == DraftPhase.IDLE
            ) {
                FirstPickDetector.detect(frame)
            } else null

            withContext(Dispatchers.Main) {
                stateHolder.autoTransitionPhase(phaseResult.phase, currentPhase, firstPickResult)
            }
        }

        val session = stateHolder.sessionValue()
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (session.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                scanAndRecordBans(frame, round)
            }
            DraftPhase.PICK -> {
                // One-shot catch-up: ban portraits remain visible at the top of
                // the pick screen. Scan them once at pick-phase entry if any were
                // missed during the ban phase (banCatchUpDone gate).
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

    /**
     * Ban slots are small (~52×52 px) circular crops. The dark corners from
     * the circular mask depress the histogram component of the hybrid score,
     * so we use a lower confidence floor (0.45f vs 0.55f for picks) to avoid
     * discarding correct matches.
     */
    private suspend fun scanAndRecordBans(frame: Bitmap, round: Int) {
        SlotRegions.enemyBanSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledEnemyBanSlots && isSlotFilled(frame, region)) {
                stateHolder.filledEnemyBanSlots.add(i)
                val hero = matchPortrait(frame, region, 0.45f)
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordEnemyBan(hero, round, i)
                }
            }
        }
        SlotRegions.ourBanSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledOurBanSlots && isSlotFilled(frame, region)) {
                stateHolder.filledOurBanSlots.add(i)
                val hero = matchPortrait(frame, region, 0.45f)
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordOurBan(hero, round, i)
                }
            }
        }
    }

    private suspend fun scanAndRecordPicks(frame: Bitmap) {
        SlotRegions.enemyPickSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledEnemyPickSlots && isSlotFilled(frame, region)) {
                stateHolder.filledEnemyPickSlots.add(i)
                val hero = matchPortrait(frame, region, 0.55f) ?: return@forEachIndexed
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordEnemyPick(hero, i)
                }
            }
        }
        SlotRegions.ourPickSlots.forEachIndexed { i, region ->
            if (i !in stateHolder.filledOurPickSlots && isSlotFilled(frame, region)) {
                stateHolder.filledOurPickSlots.add(i)
                val hero = matchPortrait(frame, region, 0.55f) ?: return@forEachIndexed
                val topId = stateHolder.recommendations.firstOrNull()?.hero?.id
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordOurPick(hero, i, followedRecommendation = hero.id == topId)
                }
            }
        }
    }

    // ── Frame utilities ───────────────────────────────────────────────────────

    private fun matchPortrait(frame: Bitmap, region: SlotRegionF, minConf: Float): Hero? {
        val crop = runCatching { SlotRegions.cropSlot(frame, region) }.getOrNull() ?: return null
        return try {
            val r = portraitMatcher.match(crop, stateHolder.allHeroes.toList())
            if (r.confidence >= minConf) r.hero else null
        } finally { crop.recycle() }
    }

    private fun isSlotFilled(frame: Bitmap, region: SlotRegionF): Boolean {
        val crop = runCatching { SlotRegions.cropSlot(frame, region) }.getOrNull() ?: return false
        return try {
            var total = 0f; var count = 0
            val step = 4
            for (x in 0 until crop.width step step) {
                for (y in 0 until crop.height step step) {
                    val px = crop.getPixel(x, y)
                    total += 0.299f * android.graphics.Color.red(px) +
                             0.587f * android.graphics.Color.green(px) +
                             0.114f * android.graphics.Color.blue(px)
                    count++
                }
            }
            count > 0 && (total / count) > 40f
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
