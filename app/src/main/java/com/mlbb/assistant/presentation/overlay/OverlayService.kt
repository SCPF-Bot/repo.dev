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
import android.app.PendingIntent
import android.provider.Settings as SystemSettings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import coil3.ImageLoader
import com.mlbb.assistant.capture.PhaseDetector
import com.mlbb.assistant.capture.PortraitMatcher
import com.mlbb.assistant.capture.SlotRegionF
import com.mlbb.assistant.capture.SlotRegions
import com.mlbb.assistant.R
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
import kotlinx.coroutines.flow.first
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
    @Inject lateinit var dataStore: DataStore<Preferences>

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

        // ── DataStore keys ────────────────────────────────────────────────
        // TD-12: Bubble position persistence
        val KEY_BUBBLE_X = floatPreferencesKey("overlay_bubble_x")
        val KEY_BUBBLE_Y = floatPreferencesKey("overlay_bubble_y")

        // 3.1.1: Session snapshot keys
        val KEY_SESSION_PHASE      = stringPreferencesKey("session_phase")
        val KEY_SESSION_RANK       = stringPreferencesKey("session_rank")
        val KEY_SESSION_FIRST_PICK = booleanPreferencesKey("session_our_team_first")

        // 3.1.2: Notification action ID
        const val ACTION_RELAUNCH_OVERLAY = "com.mlbb.assistant.ACTION_RELAUNCH_OVERLAY"

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
        // 3.1.1: Restore any in-progress session snapshot from a previous run.
        serviceScope.launch { restoreSessionSnapshot() }
        // 3.1.3: Start accessibility / overlay-permission watchdog.
        startPermissionWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        @Suppress("DEPRECATION")
        val projData: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        else
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)

        // 3.1.4: If overlay permission was revoked while the service was stopped,
        // stop immediately to avoid a WindowManager crash on addView().
        if (!SystemSettings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 3.1.2: Handle the "Relaunch Overlay" notification tap.
        if (intent?.action == ACTION_RELAUNCH_OVERLAY) {
            if (!isExpanded.value) expandToWidget()
            return START_STICKY
        }

        if (resultCode != -1 && projData != null) {
            // Upgrade the FGS type to include mediaProjection NOW that we have
            // an authorised token.  On Android 14+ this must happen AFTER the
            // user has granted consent via MediaProjectionManager — doing it
            // earlier (e.g. in onCreate) causes a SecurityException.
            upgradeFgToProjection()
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

    /**
     * Starts the foreground service notification.
     *
     * On Android 14+ (UPSIDE_DOWN_CAKE / API 34) the OS enforces that a
     * foreground service of type `mediaProjection` may ONLY be started after
     * the user has explicitly granted a MediaProjection token via the system
     * screen-capture consent dialog.  Calling `startForeground` with that type
     * in `onCreate()` — before any projection token is obtained — causes an
     * immediate SecurityException and kills the service.
     *
     * Fix: start with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` only.  When the
     * service is later invoked WITH a valid projection token (via
     * [startWithProjection] → [onStartCommand]), call [upgradeFgToProjection]
     * to add the `mediaProjection` type at that point.
     */
    private fun startFg() {
        val notif = buildServiceNotification()
        when {
            // API 34+: SPECIAL_USE only; mediaProjection is added later in
            // upgradeFgToProjection() once the user grants capture consent.
            // Passing mediaProjection here (before the token is obtained) causes
            // a SecurityException on Android 14+.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(
                    NOTIF_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
            // API 29–33: mediaProjection type can be declared before the token
            // is obtained (the token restriction was added in API 34).
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(
                    NOTIF_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
            else -> startForeground(NOTIF_ID, notif)
        }
    }

    /**
     * Upgrades the running foreground service to also include the
     * `mediaProjection` type.  Called from [onStartCommand] only after a valid
     * projection token is confirmed, satisfying Android 14's requirement.
     */
    private fun upgradeFgToProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                startForeground(
                    NOTIF_ID,
                    buildServiceNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
        }
    }

    /**
     * 3.1.2: Notification includes a "Relaunch Overlay" action so the user
     * can restore the widget after accidentally dismissing it without opening
     * the main app.
     */
    private fun buildServiceNotification(): android.app.Notification {
        val relaunchPi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_RELAUNCH_OVERLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("MLBB Draft Assistant")
            .setContentText("Draft assistant is running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.overlay_notification_action_relaunch),
                relaunchPi
            )
            .build()
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
     * TWO-MODE TOUCH STRATEGY
     * ──────────────────────────────────────────────────────────────────────────
     *
     * BUBBLE MODE (collapsed)
     * ───────────────────────
     * The bubble renders FloatingBubble which places Modifier.clickable on its
     * root Box.  When setOnTouchListener returns FALSE from ACTION_DOWN, Compose
     * arms its clickable gesture detector and claims ownership of the pointer
     * sequence.  As the finger moves, Compose's detectTapGestures and the
     * clickable ripple tracker both process MOVE events.  On some devices/ROMs
     * the internal ACTION_CANCEL we dispatch to v.onTouchEvent() does not fully
     * cancel Compose's in-flight gesture, so the window drag never starts.
     *
     * FIX — bubble mode returns TRUE from ACTION_DOWN (claims the sequence
     * outright, Compose never sees it).  Drag vs tap is detected at the View
     * level.  On tap (no drag), expandToWidget() is called DIRECTLY — there is
     * no need for Compose to handle the click because the bubble has exactly ONE
     * action.
     *
     * WIDGET MODE (expanded)
     * ──────────────────────
     * The MiniWidget contains multiple interactive Compose elements (hero chips,
     * Minimize / Close buttons, START DRAFT, team toggle).  These require Compose
     * to receive DOWN so their gesture detectors arm properly.
     *
     * FIX — widget mode returns FALSE from ACTION_DOWN (Compose sees it).
     * Sub-slop MOVEs also return FALSE.  Once the drag threshold is exceeded:
     *   1. ACTION_CANCEL is dispatched to v.onTouchEvent() → Compose tears down
     *      the in-flight gesture cleanly.
     *   2. Subsequent MOVEs return TRUE → listener owns the sequence.
     *   3. ACTION_UP after drag → TRUE (consume).
     *   4. ACTION_UP after tap  → FALSE (Compose fires the button/chip click).
     *
     * Both modes use initial-based delta (windowX = initialX + totalDx) which
     * is drift-free because FLAG_LAYOUT_NO_LIMITS prevents mid-drag clipping.
     * ViewConfiguration.scaledTouchSlop is the system-calibrated threshold.
     */
    private fun addOverlayView() {
        overlayParams = buildBubbleParams()
        // TD-12: Restore last saved bubble position from DataStore so the
        // overlay reappears where the user left it after a service restart.
        serviceScope.launch {
            val (savedX, savedY) = readSavedBubblePosition()
            withContext(Dispatchers.Main) {
                overlayParams.x = savedX
                overlayParams.y = savedY
                // Don't call updateViewLayout yet — the view is added below;
                // the initial params already carry the restored position.
            }
        }

        var initialWindowX = 0
        var initialWindowY = 0
        var initialTouchX  = 0f
        var initialTouchY  = 0f
        var isDragging     = false

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
                if (!isExpanded.value) {
                    // ════════════════════════════════════════════════════════
                    // BUBBLE MODE — view-level owns the entire sequence.
                    // Return TRUE from DOWN so Compose never arms clickable.
                    // On tap, call expandToWidget() directly.
                    // ════════════════════════════════════════════════════════
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            initialWindowX = overlayParams.x
                            initialWindowY = overlayParams.y
                            initialTouchX  = event.rawX
                            initialTouchY  = event.rawY
                            isDragging     = false
                            true  // Claim the sequence; Compose does NOT see DOWN.
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                isDragging = true
                            }
                            if (isDragging) {
                                overlayParams.x = (initialWindowX + dx).toInt()
                                overlayParams.y = (initialWindowY + dy).toInt()
                                runCatching { windowManager.updateViewLayout(v, overlayParams) }
                            }
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            val wasDragging = isDragging
                            isDragging = false
                            if (wasDragging) {
                                clampToScreen()
                                runCatching { windowManager.updateViewLayout(v, overlayParams) }
                                // TD-12: Persist bubble position after drag completes.
                                saveBubblePosition(overlayParams.x, overlayParams.y)
                            } else {
                                // Tap — expand to widget directly (no Compose involved).
                                expandToWidget()
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                            true
                        }

                        else -> false
                    }
                } else {
                    // ════════════════════════════════════════════════════════
                    // WIDGET MODE — return FALSE from DOWN so Compose can
                    // handle hero-chip taps, Start Draft, Minimize, Close.
                    // Steal the sequence only once drag threshold is exceeded.
                    // ════════════════════════════════════════════════════════
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            initialWindowX = overlayParams.x
                            initialWindowY = overlayParams.y
                            initialTouchX  = event.rawX
                            initialTouchY  = event.rawY
                            isDragging     = false
                            false  // Compose sees DOWN → gesture detectors arm.
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY

                            if (!isDragging) {
                                if (abs(dx) <= touchSlop && abs(dy) <= touchSlop) {
                                    return@setOnTouchListener false  // Still a tap candidate.
                                }
                                isDragging = true
                                // Cancel Compose's in-flight gesture so no spurious
                                // click fires when the finger lifts.
                                val cancel = MotionEvent.obtain(event).also {
                                    it.action = MotionEvent.ACTION_CANCEL
                                }
                                v.onTouchEvent(cancel)
                                cancel.recycle()
                            }

                            overlayParams.x = (initialWindowX + dx).toInt()
                            overlayParams.y = (initialWindowY + dy).toInt()
                            runCatching { windowManager.updateViewLayout(v, overlayParams) }
                            true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isDragging) {
                                clampToScreen()
                                runCatching { windowManager.updateViewLayout(v, overlayParams) }
                                isDragging = false
                                true   // Drag end — consume.
                            } else {
                                isDragging = false
                                false  // Tap — Compose fires the button/chip click.
                            }
                        }

                        else -> false
                    }
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
                // 3.1.1: Persist session snapshot after each update so the
                // service can restore an in-progress draft after a restart.
                saveSessionSnapshot(session)
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

    // ── 3.1.1 Session snapshot persistence ───────────────────────────────────

    /**
     * Saves the minimal session state needed to restore an in-progress draft
     * after the service is killed and restarted (e.g. memory pressure).
     * Only non-IDLE, non-COMPLETE phases are persisted.
     */
    private fun saveSessionSnapshot(session: DraftSession) {
        serviceScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[KEY_SESSION_PHASE]      = session.phase.name
                prefs[KEY_SESSION_RANK]       = session.rank.name
                prefs[KEY_SESSION_FIRST_PICK] = session.ourTeamFirst
            }
        }
    }

    /**
     * On startup, reads the last persisted session phase.  If an in-progress
     * draft was interrupted, the session is re-initialised to the saved rank
     * and first-pick side so the overlay resumes correctly.
     *
     * IDLE and COMPLETE phases are intentionally NOT restored — they require
     * no recovery.
     */
    private suspend fun restoreSessionSnapshot() {
        val prefs     = dataStore.data.first()
        val phaseName = prefs[KEY_SESSION_PHASE] ?: return
        val phase     = runCatching { DraftPhase.valueOf(phaseName) }.getOrNull() ?: return
        if (phase == DraftPhase.IDLE || phase == DraftPhase.COMPLETE) return

        val rankName     = prefs[KEY_SESSION_RANK] ?: Rank.UNKNOWN.name
        val rank         = runCatching { Rank.valueOf(rankName) }.getOrElse { Rank.UNKNOWN }
        val ourTeamFirst = prefs[KEY_SESSION_FIRST_PICK] ?: true

        withContext(Dispatchers.Main) {
            draftSessionManager.initSession(rank, ourTeamFirst = ourTeamFirst)
            when (phase) {
                DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> draftSessionManager.startBanPhase()
                DraftPhase.PICK, DraftPhase.TRADING             -> {
                    draftSessionManager.startBanPhase()
                    draftSessionManager.startPickPhase()
                }
                else -> {}
            }
        }
    }

    // ── 3.1.3 Permission watchdog ─────────────────────────────────────────────

    /**
     * Periodically checks that SYSTEM_ALERT_WINDOW permission is still granted.
     * If the user revokes it while the service is running, the service stops
     * itself gracefully rather than crashing on the next WindowManager call.
     *
     * Check interval: 30 seconds (low battery impact).
     */
    private fun startPermissionWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                if (!SystemSettings.canDrawOverlays(this@OverlayService)) {
                    stopSelf()
                    break
                }
            }
        }
    }

    // ── TD-12 Bubble position persistence ────────────────────────────────────

    /**
     * Saves the current bubble position to DataStore so it can be restored
     * on the next service start.  Called after each completed drag.
     */
    private fun saveBubblePosition(x: Int, y: Int) {
        serviceScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[KEY_BUBBLE_X] = x.toFloat()
                prefs[KEY_BUBBLE_Y] = y.toFloat()
            }
        }
    }

    /**
     * Reads saved bubble x/y coordinates from DataStore synchronously
     * (called once during [addOverlayView] setup on the main thread via
     * a blocking first() read launched in the service's IO scope).
     *
     * Returns the default position (0, 300) if no saved position exists.
     */
    private suspend fun readSavedBubblePosition(): Pair<Int, Int> {
        val prefs = dataStore.data.first()
        val x = prefs[KEY_BUBBLE_X]?.toInt() ?: 0
        val y = prefs[KEY_BUBBLE_Y]?.toInt() ?: 300
        return x to y
    }
}
