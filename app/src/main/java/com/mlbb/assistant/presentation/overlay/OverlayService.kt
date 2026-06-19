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
     * Creates the one ComposeView that lives for the service's entire lifetime.
     * It renders FloatingBubble in collapsed mode and MiniWidget in expanded mode.
     *
     * Drag is handled at the View level so it works in both modes without
     * interfering with child-composable click events. The trick:
     *  - ACTION_DOWN  → record finger + window start position, return true
     *  - ACTION_MOVE  → if moved > drag threshold (10 dp), drag the window
     *  - ACTION_UP    → if it was NOT a drag, re-dispatch synthetic DOWN+UP so
     *                   Compose sees a normal click on whatever was underneath
     */
    private fun addOverlayView() {
        overlayParams = buildBubbleParams()

        var startX    = 0;   var startY    = 0
        var touchX    = 0f;  var touchY    = 0f
        var isDragging = false
        // Guard against the re-entry that happens when we synthetically re-dispatch
        // a tap DOWN+UP after an ACTION_UP that wasn't a drag. Without this flag,
        // dispatchTouchEvent(fakeDown) re-invokes setOnTouchListener on the same
        // ComposeView instance, consuming the fake event and causing Compose never
        // to see the click (or, worse, an infinite dispatch loop).
        var isForwardingClick = false

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
                            onHeroSelected  = { hero -> handleManualHeroSelection(hero) }
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
                // Let synthetic re-dispatched click events through to Compose untouched.
                if (isForwardingClick) return@setOnTouchListener false

                val dragThresholdPx = 10 * resources.displayMetrics.density

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX  = overlayParams.x;  startY  = overlayParams.y
                        touchX  = event.rawX;       touchY  = event.rawY
                        isDragging = false
                        true  // claim the sequence so we receive MOVE + UP
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (!isDragging && abs(dx) < dragThresholdPx && abs(dy) < dragThresholdPx) {
                            // Not yet dragging — do not move the window
                            return@setOnTouchListener true
                        }
                        isDragging = true
                        overlayParams.x = startX + dx.toInt()
                        overlayParams.y = startY + dy.toInt()
                        if (isExpanded.value) clampToScreen()
                        windowManager.updateViewLayout(v, overlayParams)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // It was a tap — re-dispatch so Compose handles the click.
                            // isForwardingClick prevents this listener from consuming
                            // the synthetic events before Compose can see them.
                            isForwardingClick = true
                            val fakeDown = MotionEvent.obtain(
                                event.downTime, event.eventTime,
                                MotionEvent.ACTION_DOWN, event.x, event.y, 0
                            )
                            v.dispatchTouchEvent(fakeDown)
                            fakeDown.recycle()

                            val fakeUp = MotionEvent.obtain(
                                event.downTime, event.eventTime,
                                MotionEvent.ACTION_UP, event.x, event.y, 0
                            )
                            v.dispatchTouchEvent(fakeUp)
                            fakeUp.recycle()
                            isForwardingClick = false
                        }
                        isDragging = false
                        true
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
     * Widget flags: drop FLAG_LAYOUT_NO_LIMITS so the expanded card cannot
     * overflow off-screen; keep NOT_TOUCH_MODAL so touches outside the widget
     * still reach the MLBB game underneath.
     */
    private fun widgetFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

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
