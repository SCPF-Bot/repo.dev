package com.mlbb.assistant.presentation.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mlbb.assistant.capture.AspectRatioPreset
import com.mlbb.assistant.capture.BanDraftType
import com.mlbb.assistant.capture.BanSlotTemplates
import com.mlbb.assistant.capture.CvFeatureFlags
import com.mlbb.assistant.capture.FirstPickDetector
import com.mlbb.assistant.capture.PhaseDetectionConfig
import com.mlbb.assistant.capture.PhaseDetector
import com.mlbb.assistant.capture.PhaseOcrDetector
import com.mlbb.assistant.capture.PortraitMatcher
import com.mlbb.assistant.capture.SlotRegionF
import com.mlbb.assistant.capture.SlotRegions
import com.mlbb.assistant.capture.SlotType
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.service.ScreenCaptureManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    @param:ApplicationContext private val context: Context,
    private val stateHolder:         OverlayStateHolder,
    private val draftSessionManager: DraftSessionManager,
    private val dataStore:           DataStore<Preferences>,
) {

    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var portraitMatcher: PortraitMatcher
    private var captureJob: Job? = null

    // Frame counter for OCR stride — reset on session start.
    private val frameCounter = AtomicInteger(0)

    /**
     * Full OCR result from the most recent successful ML Kit pass.
     * Written on Main thread (ML Kit callback), read on Dispatchers.Default.
     * Initialised to a stable no-op sentinel rather than null to avoid
     * null-checks on every frame.
     */
    private val lastOcrResult = AtomicReference(
        PhaseOcrDetector.OcrResult(PhaseDetector.DetectedPhase.UNKNOWN, 0f)
    )

    // ── TD-16: Aspect-ratio horizontal inset ──────────────────────────────────
    //
    // MLBB's draft UI is calibrated at 20:9 (landscape reference).  On ultra-wide
    // screens (21:9) the game is pillarboxed — black bars appear on the left and
    // right, shifting the visible content inward.  SlotRegions normalised
    // coordinates assume full-screen content, so they must be adjusted by this
    // inset fraction when the effective ratio differs from the reference.
    //
    // Observed whenever the user-selected AspectRatioPreset changes in Settings.
    // horizInset = 0 means no adjustment (standard 16:9 or auto-detect ≤ 20:9).
    @Volatile private var horizInset: Float = 0f

    companion object {
        /** DataStore key — mirrors SettingsViewModel.KEY_ASPECT_RATIO. */
        private val KEY_ASPECT_RATIO = stringPreferencesKey("aspect_ratio")

        /**
         * MLBB draft UI reference aspect ratio (landscape 1600 × 720 px).
         * Slot coordinates in SlotRegions are calibrated to this ratio.
         */
        private const val REF_GAME_RATIO: Float = 20f / 9f
    }

    /**
     * Adjusts a [SlotRegionF] for a horizontal content inset caused by pillarboxing
     * on ultra-wide screens.
     *
     * When [horizInset] > 0, the normalised x-coordinates assume the full screen
     * width includes inert black bars.  This function remaps them into the active
     * content area (1 − 2·inset wide, starting at `inset`).
     *
     * A [horizInset] of 0 (standard 16:9 and most auto-detected phones) returns
     * `this` unchanged, so the fast path has no overhead.
     */
    private fun SlotRegionF.adjusted(): SlotRegionF {
        if (horizInset <= 0f) return this
        val contentWidth = 1f - 2f * horizInset
        return SlotRegionF(
            left   = horizInset + left   * contentWidth,
            top    = top,
            right  = horizInset + right  * contentWidth,
            bottom = bottom
        )
    }

    // ── Initialise capture dependencies (called by OverlayService.onCreate) ───

    fun init() {
        screenCaptureManager = ScreenCaptureManager(context)
        // PortraitMatcher loads hero portraits from bundled assets (portraits/{id}.webp)
        // — no network or disk-cache dependencies needed.
        portraitMatcher = PortraitMatcher(context)
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
        stateHolder.setCaptureUnavailable(false)

        // todo.md §2/§7: surface MediaProjection revocation as an overlay banner
        // instead of silently freezing the last frame. captureRevoked flips true
        // exactly once, from ScreenCaptureManager.MediaProjection.Callback.onStop.
        scope.launch {
            screenCaptureManager.captureRevoked.collect { revoked ->
                stateHolder.setCaptureUnavailable(revoked)
            }
        }

        // TD-16: Observe the user-selected AspectRatioPreset and recompute the
        // horizontal content-inset whenever the preference changes.  The inset is
        // 0 for standard 16:9 devices; positive for ultra-wide screens (21:9)
        // where MLBB pillarboxes its draft UI inside the screen area.
        scope.launch {
            dataStore.data
                .map { prefs -> prefs[KEY_ASPECT_RATIO] ?: AspectRatioPreset.AUTO.key }
                .distinctUntilChanged()
                .collect { key ->
                    val preset = AspectRatioPreset.fromKey(key)
                    val screenRatio =
                        if (screenCaptureManager.screenWidth > 0 && screenCaptureManager.screenHeight > 0)
                            screenCaptureManager.screenWidth.toFloat() / screenCaptureManager.screenHeight
                        else null
                    val effectiveRatio = preset.effectiveRatio(screenRatio)
                    // Pillarbox inset: only positive when game content is narrower than screen.
                    horizInset = if (effectiveRatio > REF_GAME_RATIO)
                        ((1f - REF_GAME_RATIO / effectiveRatio) / 2f).coerceAtLeast(0f)
                    else 0f
                }
        }

        launchCaptureLoop(scope)
    }

    fun stop() {
        captureJob?.cancel()
        if (::screenCaptureManager.isInitialized) {
            screenCaptureManager.stopCapture()
        }
        // Previously never called: the TFLite interpreter and the dHash/pHash/histogram
        // caches (up to 200 entries each) plus SlotAwareHasher's reference-hash map stayed
        // resident for the lifetime of the process after every overlay session ended.
        if (::portraitMatcher.isInitialized) {
            portraitMatcher.close()
        }
        stateHolder.setCaptureUnavailable(false)
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
        // Gated by CvFeatureFlags.enableOcr (Settings toggle) — when disabled,
        // lastOcrResult simply stays at its UNKNOWN/0f sentinel and every
        // downstream OCR-derived check below (override, ban-round-2 advance,
        // pick-animation skip) is a no-op, so no extra guards are needed there.
        if (CvFeatureFlags.enableOcr && count % PhaseDetectionConfig.OCR_FRAME_STRIDE == 0) {
            // Crop the top-centre band where MLBB prints "First Ban Phase",
            // "Ally Team Pick", "The match is starting soon", etc.
            // Use the wider TEXT_REGION inside PhaseOcrDetector (not phaseBanner,
            // which is too narrow at 5% height to capture full multi-line labels).
            val ocrCrop = runCatching {
                SlotRegions.cropSlot(frame, SlotRegions.ocrTextRegion)
            }.getOrNull()

            if (ocrCrop != null) {
                val ocrBitmap = ocrCrop.copy(ocrCrop.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                ocrCrop.recycle()
                // PhaseOcrDetector is callback-based (ML Kit); callback fires on Main.
                withContext(Dispatchers.Main) {
                    PhaseOcrDetector.detect(ocrBitmap) { result ->
                        lastOcrResult.set(result)
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
        val ocrResult    = lastOcrResult.get()
        val effectivePhase: PhaseDetector.DetectedPhase = run {
            if (ocrResult.phase != PhaseDetector.DetectedPhase.UNKNOWN &&
                ocrResult.confidence >= PhaseDetectionConfig.OCR_OVERRIDE_CONFIDENCE) {
                ocrResult.phase
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

                // OCR-derived BAN_ROUND_2 auto-advance:
                // When OCR explicitly reads "Second Ban Phase" while the engine is
                // still in BAN_ROUND_1, advance immediately — more reliable than
                // waiting for all round-1 slots to be confirmed filled by CV.
                if (ocrResult.isBanRound2 &&
                    currentPhase == DraftPhase.BAN_ROUND_1 &&
                    stateHolder.sessionValue().banStructure.hasRound2
                ) {
                    stateHolder.sessionValue().let { s ->
                        // Only advance if we have at least one round-1 ban detected,
                        // preventing a stale OCR result from triggering a premature advance.
                        val anyBanRecorded = s.enemyBansR1.any { it != null } ||
                                             s.ourBansR1.any  { it != null }
                        if (anyBanRecorded) stateHolder.advanceToBanRound2()
                    }
                }
            }
        }

        // ── Slot scanning ─────────────────────────────────────────────────────
        // Skip entirely when the "Selecting hero" double-pick animation is on
        // screen (screenshot 7): no hero grid is visible and the hero models in
        // the centre would cause false portrait matches in the pick slots.
        if (ocrResult.isPickAnimation) return

        val session = stateHolder.sessionValue()
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (session.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                scanAndRecordBans(frame, round)
            }
            DraftPhase.PICK -> {
                if (!stateHolder.banCatchUpDone) {
                    stateHolder.banCatchUpDone = true
                    val banTemplate = BanSlotTemplates.forRank(session.rank)
                    val slotsPerTeam = banTemplate.draftType.slotsPerTeam
                    val anyBanMissed =
                        stateHolder.filledEnemyBanSlots.size < slotsPerTeam ||
                        stateHolder.filledOurBanSlots.size   < slotsPerTeam
                    if (anyBanMissed) scanAndRecordBans(frame, round = 1)
                }
                scanAndRecordPicks(frame)
            }
            else -> {}
        }
    }

    // ── Slot scanning ─────────────────────────────────────────────────────────

    /**
     * Scans only the ban slots that are visible for the session's rank tier.
     *
     * Uses [BanSlotTemplates.forRank] to resolve the active slot subset, so
     * slots 3 and 4 are never scanned at Epic rank — those screen positions
     * contain unrelated UI elements that would otherwise produce false positives.
     */
    private suspend fun scanAndRecordBans(frame: Bitmap, round: Int) {
        val session  = stateHolder.sessionValue()
        val template = BanSlotTemplates.forRank(session.rank)

        // Apply TD-16 aspect-ratio inset so coordinates map to the active game
        // content area on pillarboxed ultra-wide screens.
        template.enemyBanSlots.forEachIndexed { i, region ->
            val adjusted = region.adjusted()
            if (i !in stateHolder.filledEnemyBanSlots && isSlotFilled(frame, adjusted)) {
                val hero = matchPortrait(frame, adjusted, 0.45f, "enemyBan$i")
                if (hero != null) {
                    stateHolder.filledEnemyBanSlots.add(i)
                    withContext(Dispatchers.Main) {
                        draftSessionManager.recordEnemyBan(hero, round, i)
                    }
                }
            }
        }
        template.ourBanSlots.forEachIndexed { i, region ->
            val adjusted = region.adjusted()
            if (i !in stateHolder.filledOurBanSlots && isSlotFilled(frame, adjusted)) {
                val hero = matchPortrait(frame, adjusted, 0.45f, "ourBan$i")
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
            val adjusted = region.adjusted()
            if (i !in stateHolder.filledEnemyPickSlots && isSlotFilled(frame, adjusted)) {
                val hero = matchPortrait(frame, adjusted, 0.55f, "enemyPick$i") ?: return@forEachIndexed
                stateHolder.filledEnemyPickSlots.add(i)
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordEnemyPick(hero, i)
                }
            }
        }
        SlotRegions.ourPickSlots.forEachIndexed { i, region ->
            val adjusted = region.adjusted()
            if (i !in stateHolder.filledOurPickSlots && isSlotFilled(frame, adjusted)) {
                val hero = matchPortrait(frame, adjusted, 0.55f, "ourPick$i") ?: return@forEachIndexed
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
        // slotKey convention: "enemyBan0"/"ourBan0" → BAN, "enemyPick0"/"ourPick0" → PICK
        // (see recommendations.md §3 — SlotType drives occlusion masking + hash fusion weights).
        val slotType = if (slotKey.contains("Ban", ignoreCase = true)) SlotType.BAN else SlotType.PICK
        return try {
            val r = portraitMatcher.match(crop, stateHolder.allHeroes.toList(), slotKey, slotType)
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

            // L-01 fix: bulk getPixels() read instead of per-pixel getPixel(x, y).
            // This runs on every scanned slot on every captured frame, so the
            // per-pixel JNI overhead compounds fast (up to 8 slots x ~4 fps).
            val w = crop.width
            val h = crop.height
            val pixels = IntArray(w * h)
            crop.getPixels(pixels, 0, w, 0, 0, w, h)

            for (x in 0 until w step step) {
                for (y in 0 until h step step) {
                    val px = pixels[y * w + x]
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
