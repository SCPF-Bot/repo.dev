package com.mlbb.assistant.presentation.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.ImageLoader
import com.mlbb.assistant.capture.PhaseDetector
import com.mlbb.assistant.capture.PortraitMatcher
import com.mlbb.assistant.capture.SlotRegionF
import com.mlbb.assistant.capture.SlotRegions
import com.mlbb.assistant.domain.advisor.BanRecommender
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.engine.Rank
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.DraftScorer
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.service.ScreenCaptureManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    // ── Hilt ──────────────────────────────────────────────────────────────────
    @Inject lateinit var draftSessionManager: DraftSessionManager
    @Inject lateinit var getHeroesUseCase: GetHeroesUseCase

    // ── Lifecycle + SavedState (ComposeView in a Service requires both) ───────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val ssr = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssr.savedStateRegistry

    // ── Window / view ─────────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager

    /** The single ComposeView that renders either FloatingBubble or MiniWidget. */
    private var overlayView: ComposeView? = null

    /** Kept as a field so touch listener can update it when dragging. */
    private lateinit var overlayParams: WindowManager.LayoutParams

    // ── Capture ───────────────────────────────────────────────────────────────
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var portraitMatcher: PortraitMatcher
    private var captureJob: Job? = null

    // ── Coroutine scope ───────────────────────────────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Shared Compose state (observed by the single ComposeView) ─────────────
    private val allHeroes       = mutableStateListOf<Hero>()
    private val recommendations = mutableStateListOf<HeroScore>()
    private val banSuggestions  = mutableStateListOf<BanSuggestion>()
    private val enemyWarnings   = mutableStateListOf<String>()
    private val isExpanded      = mutableStateOf(false)
    private val isBanTurn       = mutableStateOf(false)

    // ── Autonomous detection state ────────────────────────────────────────────
    private val filledEnemyBanSlots  = mutableSetOf<Int>()
    private val filledOurBanSlots    = mutableSetOf<Int>()
    private val filledEnemyPickSlots = mutableSetOf<Int>()
    private val filledOurPickSlots   = mutableSetOf<Int>()

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val NOTIF_CHANNEL       = "overlay_channel"
        private const val NOTIF_ID            = 1
        const val EXTRA_RESULT_CODE     = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, OverlayService::class.java))

        fun startWithProjection(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, OverlayService::class.java).apply {
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_PROJECTION_DATA, data)
                }
            )
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, OverlayService::class.java))
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        // SavedState must be set up before any ComposeView is added.
        ssr.performAttach()
        ssr.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager        = getSystemService(WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(this)
        portraitMatcher      = PortraitMatcher(
            applicationContext,
            ImageLoader.Builder(applicationContext).build()
        )

        createNotificationChannel()
        startFg()
        addOverlayView()
        loadHeroes()
        observeSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        @Suppress("DEPRECATION")
        val projData: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        else
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)

        if (resultCode != -1 && projData != null) {
            screenCaptureManager.startCapture(resultCode, projData)
            launchCaptureLoop()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        screenCaptureManager.stopCapture()
        serviceScope.cancel()
        removeOverlayView()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun startFg() {
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("MLBB Draft Assistant")
            .setContentText("Draft assistant is running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            NOTIF_CHANNEL, "Overlay", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "MLBB Draft Assistant overlay" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    // ── Single overlay view (bubble ↔ mini-widget) ────────────────────────────

    /**
     * Creates the ComposeView that lives for the service's entire lifetime and
     * renders either FloatingBubble (collapsed) or MiniWidget (expanded).
     *
     * ──────────────────────────────────────────────────────────────────────────
     * DRAG DESIGN — why previous approaches failed
     * ──────────────────────────────────────────────────────────────────────────
     * The fundamental contract of Android's View touch system is:
     *
     *   • A View only remains in the active touch sequence if it returns TRUE
     *     from onTouchEvent (or its listener) on ACTION_DOWN.
     *   • If the listener returns TRUE on ACTION_DOWN it CONSUMES the event —
     *     ComposeView's own onTouchEvent is never called, so Compose never
     *     registers its gesture detectors and NO click/ripple can fire.
     *   • Synthetic re-dispatch (fake DOWN+UP) is unreliable: Compose's pointer
     *     input pipeline does not treat synthetic events identically to real ones
     *     — in particular, the pointer ID / event time pairing can differ, and
     *     the re-entrant dispatch path through setOnTouchListener itself can
     *     silently swallow the fake events.
     *
     * ──────────────────────────────────────────────────────────────────────────
     * CORRECT PATTERN (per Android WindowManager docs + AOSP BubbleTouchHandler)
     * ──────────────────────────────────────────────────────────────────────────
     * 1. Return FALSE on ACTION_DOWN — Compose sees the DOWN, registers its
     *    gesture detectors, and the ripple / pressedState begins.
     * 2. Return FALSE on each ACTION_MOVE until the finger exceeds
     *    ViewConfiguration.scaledTouchSlop — Compose continues tracking.
     * 3. The moment drag is confirmed, dispatch ACTION_CANCEL to the View
     *    (v.onTouchEvent) so Compose cleanly tears down its in-flight gesture
     *    (ripple, long-press, click) without firing a spurious callback.
     * 4. Return TRUE on all subsequent ACTION_MOVE — we now "own" the sequence;
     *    Compose correctly ignores them because it received the CANCEL.
     * 5. Return FALSE on ACTION_UP when NOT dragging — Compose sees UP after
     *    the DOWN it received, and fires the click/tap handler normally.
     * 6. Return TRUE on ACTION_UP when dragging — clamp window position, done.
     *
     * Using ViewConfiguration.scaledTouchSlop (system-defined, accounts for
     * screen density and accessibility "fat-finger" adjustments) is more correct
     * than a hardcoded pixel threshold.
     *
     * Using initial-based delta (windowX = initialWindowX + totalFingerDx) is
     * more accurate than per-event delta because it eliminates floating-point
     * truncation that accumulates over many small MOVE events on slow paths.
     * FLAG_LAYOUT_NO_LIMITS on both modes ensures WindowManager never clips the
     * position mid-drag (clampToScreen() runs on ACTION_UP instead).
     */
    private fun addOverlayView() {
        overlayParams = buildBubbleParams()

        var initialWindowX = 0
        var initialWindowY = 0
        var initialTouchX  = 0f
        var initialTouchY  = 0f
        var isDragging     = false

        // System-calibrated touch slop — the minimum finger travel that
        // distinguishes an intentional drag from an accidental finger wobble.
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                MLBBAssistantTheme {
                    val session by draftSessionManager.session.collectAsState()
                    if (isExpanded.value) {
                        MiniWidget(
                            session         = session,
                            recommendations = recommendations.toList(),
                            banSuggestions  = banSuggestions.toList(),
                            isBanTurn       = isBanTurn.value,
                            enemyWarnings   = enemyWarnings.toList(),
                            onMinimize      = { collapseTobubble() },
                            onClose         = { stopSelf() },
                            onHeroSelected  = { hero -> handleManualHeroSelection(hero) },
                            onStartDraft    = { ourTeamFirst -> handleManualDraftStart(ourTeamFirst) }
                        )
                    } else {
                        FloatingBubble(
                            session = session,
                            onTap   = { expandToWidget() }
                        )
                    }
                }
            }

            setOnTouchListener { v, event ->
                when (event.actionMasked) {

                    // ── DOWN ────────────────────────────────────────────────
                    // Return FALSE so ComposeView.onTouchEvent also receives the
                    // DOWN event.  This is what arms Compose's gesture detectors
                    // (click, ripple, long-press).  We record the starting
                    // positions but do not yet "claim" the touch sequence.
                    MotionEvent.ACTION_DOWN -> {
                        initialWindowX = overlayParams.x
                        initialWindowY = overlayParams.y
                        initialTouchX  = event.rawX
                        initialTouchY  = event.rawY
                        isDragging     = false
                        false
                    }

                    // ── MOVE ────────────────────────────────────────────────
                    MotionEvent.ACTION_MOVE -> {
                        val totalDx = event.rawX - initialTouchX
                        val totalDy = event.rawY - initialTouchY

                        if (!isDragging) {
                            if (abs(totalDx) <= touchSlop && abs(totalDy) <= touchSlop) {
                                // Still within tap-slop: pass to Compose.
                                return@setOnTouchListener false
                            }
                            // Threshold exceeded → drag begins.
                            isDragging = true

                            // Cancel Compose's in-flight gesture so it does not
                            // fire a spurious click or keep the ripple active.
                            val cancel = MotionEvent.obtain(event).also {
                                it.action = MotionEvent.ACTION_CANCEL
                            }
                            v.onTouchEvent(cancel)
                            cancel.recycle()
                        }

                        // Move the window: initial-based delta is drift-free
                        // because FLAG_LAYOUT_NO_LIMITS prevents any clipping.
                        overlayParams.x = (initialWindowX + totalDx).toInt()
                        overlayParams.y = (initialWindowY + totalDy).toInt()
                        runCatching { windowManager.updateViewLayout(v, overlayParams) }
                        true  // We own the sequence from here on.
                    }

                    // ── UP / CANCEL ─────────────────────────────────────────
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            // Snap to safe bounds after the finger lifts.
                            clampToScreen()
                            runCatching { windowManager.updateViewLayout(v, overlayParams) }
                            isDragging = false
                            true  // Consume — this was a drag, not a tap.
                        } else {
                            isDragging = false
                            // Return FALSE: Compose has seen DOWN + (zero or sub-slop
                            // MOVEs) + UP, which is a complete tap gesture.  Compose
                            // fires the click handler (expandToWidget / button tap)
                            // naturally without any synthetic re-dispatch.
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        windowManager.addView(overlayView, overlayParams)
    }

    private fun removeOverlayView() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    // ── Expand / collapse ─────────────────────────────────────────────────────

    /**
     * Switches the view from FloatingBubble → MiniWidget.
     * Updates WindowManager flags so touches outside the widget pass through
     * to the MLBB game, and clamps position so the widget stays on screen.
     */
    private fun expandToWidget() {
        isExpanded.value = true
        overlayParams.flags = widgetFlags()
        clampToScreen()
        windowManager.updateViewLayout(overlayView ?: return, overlayParams)
    }

    /** Switches the view from MiniWidget → FloatingBubble. */
    private fun collapseTobubble() {
        isExpanded.value = false
        overlayParams.flags = bubbleFlags()
        windowManager.updateViewLayout(overlayView ?: return, overlayParams)
    }

    // ── WindowManager param helpers ───────────────────────────────────────────

    private fun buildBubbleParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        bubbleFlags(),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

    /**
     * Bubble flags: allow no-limits so the bubble can be dragged to screen edges;
     * FLAG_NOT_FOCUSABLE so it never steals keyboard focus from the game.
     */
    private fun bubbleFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    /**
     * Widget flags: keep FLAG_LAYOUT_NO_LIMITS so the window manager never
     * clips position while a finger is mid-drag. We call clampToScreen() on
     * ACTION_UP instead, which gives smooth dragging with a safe resting place.
     * FLAG_NOT_TOUCH_MODAL ensures touches outside the widget reach MLBB below.
     */
    private fun widgetFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /**
     * After expanding, clamp the window position so the widget (≈280 dp wide)
     * is fully visible on screen.
     */
    private fun clampToScreen() {
        val dm      = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val widgetW = (280 * dm.density).toInt()
        val widgetH = (200 * dm.density).toInt()
        overlayParams.x = overlayParams.x.coerceIn(0, (screenW - widgetW).coerceAtLeast(0))
        overlayParams.y = overlayParams.y.coerceIn(0, (screenH - widgetH).coerceAtLeast(0))
    }

    // ── Manual draft controls from mini-widget ────────────────────────────────

    /**
     * Called when the user taps "START DRAFT" in the mini-widget's idle body.
     *
     * Initialises a new session with [Rank.UNKNOWN] (auto-upgraded once ban
     * counts are observed) and immediately transitions to BAN_ROUND_1 so the
     * widget shows live recommendations without waiting for screen-capture
     * phase detection.
     *
     * @param ourTeamFirst true = allied team picks first (ally button selected);
     *                     false = enemy team picks first (enemy button selected).
     */
    private fun handleManualDraftStart(ourTeamFirst: Boolean) {
        resetDraftTracking()
        draftSessionManager.initSession(Rank.UNKNOWN, ourTeamFirst = ourTeamFirst)
        draftSessionManager.startBanPhase()
        // Ensure widget is expanded and on-screen.
        if (!isExpanded.value) expandToWidget()
    }

    // ── Manual hero selection from mini-widget tap ────────────────────────────

    private fun handleManualHeroSelection(hero: Hero) {
        val s = draftSessionManager.session.value
        when (s.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (s.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                val bans  = if (round == 1) s.ourBansR1 else s.ourBansR2
                val slot  = bans.indexOfFirst { it == null }
                if (slot >= 0) draftSessionManager.recordOurBan(hero, round, slot)
            }
            DraftPhase.PICK -> {
                val slot = s.ourPicks.indexOfFirst { it == null }
                if (slot >= 0) {
                    val topId = recommendations.firstOrNull()?.hero?.id
                    draftSessionManager.recordOurPick(hero, slot, hero.id == topId)
                }
            }
            else -> {}
        }
    }

    // ── Hero loading ──────────────────────────────────────────────────────────

    private fun loadHeroes() {
        serviceScope.launch {
            getHeroesUseCase().collectLatest { heroes ->
                allHeroes.clear()
                allHeroes.addAll(heroes)
                launch(Dispatchers.IO) {
                    runCatching { portraitMatcher.preloadHashes(heroes) }
                }
                refreshRecommendations(draftSessionManager.session.value)
            }
        }
    }

    private fun observeSession() {
        serviceScope.launch {
            draftSessionManager.session.collectLatest { session ->
                refreshRecommendations(session)
                // Auto-expand to widget when a draft starts
                if (session.phase == DraftPhase.BAN_ROUND_1 && !isExpanded.value) {
                    expandToWidget()
                }
            }
        }
    }

    private fun refreshRecommendations(session: DraftSession) {
        val w = ScoreWeights.DEFAULT
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                banSuggestions.clear()
                banSuggestions.addAll(
                    BanRecommender.rank(
                        availableHeroes = allHeroes,
                        bannedIds       = session.allBannedHeroes.map { it.id }.toSet(),
                        pickedIds       = session.allPickedHeroes.map { it.id }.toSet(),
                        weights         = w
                    )
                )
                enemyWarnings.clear()
                enemyWarnings.addAll(
                    CompositionAnalyzer.analyze(session.enemyPickedHeroes).warnings
                )
            }
            DraftPhase.PICK -> {
                recommendations.clear()
                recommendations.addAll(
                    DraftScorer.rankAll(
                        pool        = allHeroes,
                        alliedPicks = session.ourPickedHeroes,
                        enemyPicks  = session.enemyPickedHeroes,
                        bannedIds   = session.unavailableIds,
                        weights     = w,
                        currentTurn = session.currentTurn
                    ).take(10)
                )
                enemyWarnings.clear()
                enemyWarnings.addAll(
                    CompositionAnalyzer.analyze(session.enemyPickedHeroes).warnings
                )
            }
            else -> {}
        }
    }

    // ── Autonomous screen-capture loop ────────────────────────────────────────

    private fun launchCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch(Dispatchers.IO) {
            while (allHeroes.isEmpty() && isActive) delay(300)

            while (isActive) {
                val phase   = draftSessionManager.session.value.phase
                val delayMs = if (phase == DraftPhase.IDLE || phase == DraftPhase.COMPLETE) 2000L else 500L
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

    private suspend fun analyzeFrame(frame: Bitmap, currentPhase: DraftPhase) {
        val phaseResult      = PhaseDetector.detect(frame)
        val banButtonVisible = isBanButtonVisible(frame)

        withContext(Dispatchers.Main) { isBanTurn.value = banButtonVisible }

        if (phaseResult.confidence > 0.5f) {
            withContext(Dispatchers.Main) {
                autoTransitionPhase(phaseResult.phase, currentPhase)
            }
        }

        val session = draftSessionManager.session.value
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (session.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                scanAndRecordBans(frame, round)
            }
            DraftPhase.PICK -> scanAndRecordPicks(frame)
            else -> {}
        }
    }

    // ── Phase auto-transition ─────────────────────────────────────────────────

    private fun autoTransitionPhase(detected: PhaseDetector.DetectedPhase, current: DraftPhase) {
        when {
            detected == PhaseDetector.DetectedPhase.BAN && current == DraftPhase.IDLE -> {
                resetDraftTracking()
                draftSessionManager.initSession(Rank.UNKNOWN, ourTeamFirst = true)
                draftSessionManager.startBanPhase()
            }
            detected == PhaseDetector.DetectedPhase.BAN && current == DraftPhase.BAN_ROUND_1 -> {
                val s    = draftSessionManager.session.value
                val done = s.enemyBansR1.all { it != null } && s.ourBansR1.all { it != null }
                if (done && s.banStructure.hasRound2) draftSessionManager.startBanRound2()
            }
            detected == PhaseDetector.DetectedPhase.PICK &&
            current in setOf(DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2) -> {
                filledEnemyPickSlots.clear(); filledOurPickSlots.clear()
                draftSessionManager.startPickPhase()
            }
            detected == PhaseDetector.DetectedPhase.TRADING && current == DraftPhase.PICK ->
                draftSessionManager.startTradingPhase()
            detected == PhaseDetector.DetectedPhase.LOADING &&
            current in setOf(DraftPhase.PICK, DraftPhase.TRADING) ->
                draftSessionManager.completeDraft()
        }
    }

    // ── Slot scanning ─────────────────────────────────────────────────────────

    private suspend fun scanAndRecordBans(frame: Bitmap, round: Int) {
        SlotRegions.enemyBanSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyBanSlots && isSlotFilled(frame, region)) {
                filledEnemyBanSlots.add(i)
                val hero = matchPortrait(frame, region, 0.65f)
                withContext(Dispatchers.Main) { draftSessionManager.recordEnemyBan(hero, round, i) }
            }
        }
        SlotRegions.ourBanSlots.forEachIndexed { i, region ->
            if (i !in filledOurBanSlots && isSlotFilled(frame, region)) {
                filledOurBanSlots.add(i)
                val hero = matchPortrait(frame, region, 0.65f)
                withContext(Dispatchers.Main) { draftSessionManager.recordOurBan(hero, round, i) }
            }
        }
    }

    private suspend fun scanAndRecordPicks(frame: Bitmap) {
        SlotRegions.enemyPickSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyPickSlots && isSlotFilled(frame, region)) {
                filledEnemyPickSlots.add(i)
                val hero = matchPortrait(frame, region, 0.55f) ?: return@forEachIndexed
                withContext(Dispatchers.Main) { draftSessionManager.recordEnemyPick(hero, i) }
            }
        }
        SlotRegions.ourPickSlots.forEachIndexed { i, region ->
            if (i !in filledOurPickSlots && isSlotFilled(frame, region)) {
                filledOurPickSlots.add(i)
                val hero = matchPortrait(frame, region, 0.55f) ?: return@forEachIndexed
                val topId = recommendations.firstOrNull()?.hero?.id
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
            val r = portraitMatcher.match(crop, allHeroes.toList())
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

    private fun resetDraftTracking() {
        filledEnemyBanSlots.clear(); filledOurBanSlots.clear()
        filledEnemyPickSlots.clear(); filledOurPickSlots.clear()
    }
}
