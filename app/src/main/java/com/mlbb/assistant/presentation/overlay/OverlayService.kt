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
import android.os.Bundle
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

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    // ── Hilt injections ───────────────────────────────────────────────────────
    @Inject lateinit var draftSessionManager: DraftSessionManager
    @Inject lateinit var getHeroesUseCase: GetHeroesUseCase

    // ── Lifecycle + SavedState (required for ComposeView in a Service) ────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val ssr = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssr.savedStateRegistry

    // ── Android plumbing ──────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var portraitMatcher: PortraitMatcher
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Shared UI state (observed by ComposeViews) ────────────────────────────
    private val allHeroes       = mutableStateListOf<Hero>()
    private val recommendations = mutableStateListOf<HeroScore>()
    private val banSuggestions  = mutableStateListOf<BanSuggestion>()
    private val enemyWarnings   = mutableStateListOf<String>()
    private val isExpanded      = mutableStateOf(false)
    private val isBanTurn       = mutableStateOf(false)

    // ── Screen capture / frame analysis state ─────────────────────────────────
    private var captureJob: Job? = null

    // Slot fill tracking — mirrors FrameProcessor but kept here so portrait
    // matching can use the same frame before it is recycled.
    private val filledEnemyBanSlots  = mutableSetOf<Int>()
    private val filledOurBanSlots    = mutableSetOf<Int>()
    private val filledEnemyPickSlots = mutableSetOf<Int>()
    private val filledOurPickSlots   = mutableSetOf<Int>()

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val NOTIF_CHANNEL = "overlay_channel"
        private const val NOTIF_ID      = 1
        const val EXTRA_RESULT_CODE     = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, OverlayService::class.java))

        /** Start (or re-deliver to an already-running) service with projection data. */
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
        // SavedState must be attached/restored before any ComposeView is created.
        ssr.performAttach()
        ssr.performRestore(null)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager           = getSystemService(WINDOW_SERVICE) as WindowManager
        screenCaptureManager    = ScreenCaptureManager(this)
        portraitMatcher         = PortraitMatcher(
            applicationContext,
            ImageLoader.Builder(applicationContext).build()
        )

        createNotificationChannel()
        startFg()
        addBubble()
        loadHeroes()
        observeSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // If a MediaProjection grant was delivered (user tapped "Start Draft" and
        // accepted the screen-capture dialog), start the autonomous capture loop.
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
        removeBubble()
        removePanel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Foreground notification ───────────────────────────────────────────────

    private fun startFg() {
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("MLBB Assistant Active")
            .setContentText("Draft assistant is running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "Overlay Service", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "MLBB Draft Assistant overlay" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    // ── Hero loading ──────────────────────────────────────────────────────────

    private fun loadHeroes() {
        serviceScope.launch {
            getHeroesUseCase().collectLatest { heroes ->
                allHeroes.clear()
                allHeroes.addAll(heroes)
                // Preload portrait hashes in background so matching is fast.
                launch(Dispatchers.IO) {
                    runCatching { portraitMatcher.preloadHashes(heroes) }
                }
                refreshRecommendations(draftSessionManager.session.value)
            }
        }
    }

    // ── Session observation → refresh recommendations ─────────────────────────

    private fun observeSession() {
        serviceScope.launch {
            draftSessionManager.session.collectLatest { session ->
                refreshRecommendations(session)
            }
        }
    }

    private fun refreshRecommendations(session: DraftSession) {
        val weights = ScoreWeights.DEFAULT
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                banSuggestions.clear()
                banSuggestions.addAll(
                    BanRecommender.rank(
                        availableHeroes = allHeroes,
                        bannedIds       = session.allBannedHeroes.map { it.id }.toSet(),
                        pickedIds       = session.allPickedHeroes.map { it.id }.toSet(),
                        weights         = weights
                    )
                )
                enemyWarnings.clear()
                enemyWarnings.addAll(CompositionAnalyzer.analyze(session.enemyPickedHeroes).warnings)
            }
            DraftPhase.PICK -> {
                recommendations.clear()
                recommendations.addAll(
                    DraftScorer.rankAll(
                        pool        = allHeroes,
                        alliedPicks = session.ourPickedHeroes,
                        enemyPicks  = session.enemyPickedHeroes,
                        bannedIds   = session.unavailableIds,
                        weights     = weights,
                        currentTurn = session.currentTurn
                    ).take(10)
                )
                enemyWarnings.clear()
                enemyWarnings.addAll(CompositionAnalyzer.analyze(session.enemyPickedHeroes).warnings)
            }
            else -> {}
        }
    }

    // ── Bubble ────────────────────────────────────────────────────────────────

    private fun addBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                MLBBAssistantTheme {
                    FloatingBubble(
                        session    = draftSessionManager.session.collectAsState().value,
                        isExpanded = isExpanded.value,
                        onTap      = {
                            isExpanded.value = !isExpanded.value
                            if (isExpanded.value) addPanel() else removePanel()
                        }
                    )
                }
            }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        touchX = event.rawX; touchY = event.rawY; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(this, params); true
                    }
                    else -> false
                }
            }
        }
        windowManager.addView(bubbleView, params)
    }

    private fun removeBubble() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    private fun addPanel() {
        if (panelView != null) return
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 40 }

        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                MLBBAssistantTheme {
                    DraftPanel(
                        session         = draftSessionManager.session.collectAsState().value,
                        recommendations = recommendations.toList(),
                        banSuggestions  = banSuggestions.toList(),
                        allHeroes       = allHeroes.toList(),
                        enemyWarnings   = enemyWarnings.toList(),
                        isBanTurn       = isBanTurn.value,
                        onHeroSelected  = { hero -> handleHeroSelectedManually(hero) },
                        onHeroLongPress = {},
                        onMinimize      = { isExpanded.value = false; removePanel() },
                        onClose         = { stopSelf() }
                    )
                }
            }
        }
        windowManager.addView(panelView, params)
    }

    private fun removePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
    }

    // ── Manual hero selection (from panel grid/tap) ───────────────────────────

    private fun handleHeroSelectedManually(hero: Hero) {
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

    // ── Autonomous screen capture loop ────────────────────────────────────────

    /**
     * Runs a continuous capture → analyze → update cycle on the IO/Default
     * dispatcher. Phase transitions and DraftSessionManager updates are
     * switched back to Main before being applied.
     *
     * Throttled to 2 fps during active draft, 0.5 fps when idle/complete.
     */
    private fun launchCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch(Dispatchers.IO) {
            // Wait until hero data is available for portrait matching.
            while (allHeroes.isEmpty() && isActive) { delay(300) }

            while (isActive) {
                val phase    = draftSessionManager.session.value.phase
                val delayMs  = if (phase == DraftPhase.IDLE || phase == DraftPhase.COMPLETE) 2000L else 500L
                val frame    = screenCaptureManager.captureFrame()

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

    /**
     * Full frame analysis pipeline.
     * 1. Detect current draft phase from pixel colours.
     * 2. Auto-transition DraftSessionManager to the new phase.
     * 3. Scan every ban/pick slot for fill changes.
     * 4. Run portrait matching on newly filled slots.
     * 5. Record detected heroes into DraftSessionManager.
     *
     * Always called on Dispatchers.Default; state writes hop to Main.
     */
    private suspend fun analyzeFrame(frame: Bitmap, currentPhase: DraftPhase) {
        // ── 1. Phase detection ──────────────────────────────────────────────
        val phaseResult      = PhaseDetector.detect(frame)
        val banButtonVisible = isBanButtonVisible(frame)

        withContext(Dispatchers.Main) {
            isBanTurn.value = banButtonVisible
        }

        // ── 2. Phase auto-transition ────────────────────────────────────────
        if (phaseResult.confidence > 0.5f) {
            withContext(Dispatchers.Main) {
                autoTransitionPhase(phaseResult.phase, currentPhase)
            }
        }

        val session = draftSessionManager.session.value

        // ── 3+4+5. Slot scanning + portrait matching + recording ────────────
        when (session.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val round = if (session.phase == DraftPhase.BAN_ROUND_2) 2 else 1
                scanAndRecordBans(frame, round)
            }
            DraftPhase.PICK -> scanAndRecordPicks(frame)
            else -> {}
        }
    }

    // ── Phase auto-transition logic ───────────────────────────────────────────

    /** Must be called on Main. */
    private fun autoTransitionPhase(
        detected: PhaseDetector.DetectedPhase,
        current:  DraftPhase
    ) {
        when {
            // Game just entered ban phase — init session automatically.
            detected == PhaseDetector.DetectedPhase.BAN &&
            current  == DraftPhase.IDLE -> {
                resetDraftTracking()
                draftSessionManager.initSession(Rank.UNKNOWN, ourTeamFirst = true)
                draftSessionManager.startBanPhase()
            }
            // Round-2 bans — all R1 bans filled and still seeing BAN ui.
            detected == PhaseDetector.DetectedPhase.BAN &&
            current  == DraftPhase.BAN_ROUND_1 -> {
                val s = draftSessionManager.session.value
                val r1Done = s.enemyBansR1.all { it != null } &&
                             s.ourBansR1.all   { it != null }
                if (r1Done && s.banStructure.hasRound2) {
                    resetBanTrackingR2()
                    draftSessionManager.startBanRound2()
                }
            }
            // Bans finished → pick phase begins.
            detected == PhaseDetector.DetectedPhase.PICK &&
            current  in setOf(DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2) -> {
                filledEnemyPickSlots.clear()
                filledOurPickSlots.clear()
                draftSessionManager.startPickPhase()
            }
            // Trading phase.
            detected == PhaseDetector.DetectedPhase.TRADING &&
            current  == DraftPhase.PICK -> {
                draftSessionManager.startTradingPhase()
            }
            // Loading screen → draft complete.
            detected == PhaseDetector.DetectedPhase.LOADING &&
            current  in setOf(DraftPhase.PICK, DraftPhase.TRADING) -> {
                draftSessionManager.completeDraft()
            }
        }
    }

    // ── Slot scanning helpers ─────────────────────────────────────────────────

    private suspend fun scanAndRecordBans(frame: Bitmap, round: Int) {
        SlotRegions.enemyBanSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyBanSlots && isSlotFilled(frame, region)) {
                filledEnemyBanSlots.add(i)
                val hero = matchPortrait(frame, region, minConfidence = 0.65f)
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordEnemyBan(hero, round, i)
                }
            }
        }
        SlotRegions.ourBanSlots.forEachIndexed { i, region ->
            if (i !in filledOurBanSlots && isSlotFilled(frame, region)) {
                filledOurBanSlots.add(i)
                val hero = matchPortrait(frame, region, minConfidence = 0.65f)
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordOurBan(hero, round, i)
                }
            }
        }
    }

    private suspend fun scanAndRecordPicks(frame: Bitmap) {
        SlotRegions.enemyPickSlots.forEachIndexed { i, region ->
            if (i !in filledEnemyPickSlots && isSlotFilled(frame, region)) {
                filledEnemyPickSlots.add(i)
                val hero = matchPortrait(frame, region, minConfidence = 0.55f) ?: return@forEachIndexed
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordEnemyPick(hero, i)
                }
            }
        }
        SlotRegions.ourPickSlots.forEachIndexed { i, region ->
            if (i !in filledOurPickSlots && isSlotFilled(frame, region)) {
                filledOurPickSlots.add(i)
                val hero = matchPortrait(frame, region, minConfidence = 0.55f) ?: return@forEachIndexed
                val topId = recommendations.firstOrNull()?.hero?.id
                withContext(Dispatchers.Main) {
                    draftSessionManager.recordOurPick(hero, i, followedRecommendation = hero.id == topId)
                }
            }
        }
    }

    // ── Frame analysis utilities ──────────────────────────────────────────────

    /** Returns the matched hero, or null if confidence is below [minConfidence]. */
    private fun matchPortrait(frame: Bitmap, region: SlotRegionF, minConfidence: Float): Hero? {
        val crop = runCatching { SlotRegions.cropSlot(frame, region) }.getOrNull() ?: return null
        return try {
            val result = portraitMatcher.match(crop, allHeroes.toList())
            if (result.confidence >= minConfidence) result.hero else null
        } finally {
            crop.recycle()
        }
    }

    /** A slot is "filled" when its mean luminance exceeds the dark background threshold (40/255). */
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
        } finally {
            crop.recycle()
        }
    }

    /** Detect if the ban button (red) is visible in the action-button region. */
    private fun isBanButtonVisible(frame: Bitmap): Boolean {
        val crop = runCatching { SlotRegions.cropSlot(frame, SlotRegions.actionButton) }.getOrNull() ?: return false
        return try {
            val result = PhaseDetector.detect(crop)
            result.phase == PhaseDetector.DetectedPhase.BAN && result.confidence > 0.05f
        } finally {
            crop.recycle()
        }
    }

    // ── Slot tracking resets ──────────────────────────────────────────────────

    /** Full reset for a brand-new draft. */
    private fun resetDraftTracking() {
        filledEnemyBanSlots.clear()
        filledOurBanSlots.clear()
        filledEnemyPickSlots.clear()
        filledOurPickSlots.clear()
    }

    /** Partial reset when advancing to ban round 2 — keep R1 slots as already filled. */
    private fun resetBanTrackingR2() {
        // R1 slots stay in filledEnemy/OurBanSlots to avoid re-detecting them.
        // Pick slots not yet relevant.
    }
}
