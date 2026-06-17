package com.mlbb.assistant.presentation.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.mlbb.assistant.domain.advisor.BanRecommender
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.advisor.CompositionAnalyzer
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.DraftScorer
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner {

    // ── Hilt injections ───────────────────────────────────────────────────────
    @Inject lateinit var draftSessionManager: DraftSessionManager
    @Inject lateinit var getHeroesUseCase: GetHeroesUseCase

    // ── Android plumbing ──────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var bubbleView:  ComposeView? = null
    private var panelView:   ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Shared state ──────────────────────────────────────────────────────────
    private val allHeroes       = mutableStateListOf<Hero>()
    private val recommendations = mutableStateListOf<HeroScore>()
    private val banSuggestions  = mutableStateListOf<BanSuggestion>()
    private val enemyWarnings   = mutableStateListOf<String>()
    private var isExpanded      = mutableStateOf(false)
    private var isBanTurn       = mutableStateOf(false)

    companion object {
        private const val NOTIF_CHANNEL = "overlay_channel"
        private const val NOTIF_ID      = 1
        fun start(context: Context) =
            context.startForegroundService(Intent(context, OverlayService::class.java))
        fun stop(context: Context)  =
            context.stopService(Intent(context, OverlayService::class.java))
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startFg()
        addBubble()
        loadHeroes()
        observeSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_STICKY
    }

    override fun onDestroy() {
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
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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

    /**
     * Collects hero data from the repository and keeps [allHeroes] up-to-date.
     * Without this, recommendations and ban suggestions were always empty.
     */
    private fun loadHeroes() {
        serviceScope.launch {
            getHeroesUseCase().collectLatest { heroes ->
                allHeroes.clear()
                allHeroes.addAll(heroes)
                refreshRecommendations(draftSessionManager.session.value)
            }
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
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }

        var initialX = 0; var initialY = 0
        var touchX   = 0f; var touchY  = 0f

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)

            setContent {
                MLBBAssistantTheme {
                    FloatingBubble(
                        session    = draftSessionManager.session.collectAsState().value,
                        isExpanded = isExpanded.value,
                        onTap      = { isExpanded.value = !isExpanded.value; if (isExpanded.value) addPanel() else removePanel() }
                    )
                }
            }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN  -> { initialX = params.x; initialY = params.y; touchX = event.rawX; touchY = event.rawY; true }
                    MotionEvent.ACTION_MOVE  -> {
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

    private fun removeBubble() { bubbleView?.let { windowManager.removeView(it) }; bubbleView = null }

    // ── Panel ─────────────────────────────────────────────────────────────────

    private fun addPanel() {
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 40
        }

        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setContent {
                MLBBAssistantTheme {
                    DraftPanel(
                        session         = draftSessionManager.session.collectAsState().value,
                        recommendations = recommendations.toList(),
                        banSuggestions  = banSuggestions.toList(),
                        allHeroes       = allHeroes.toList(),
                        enemyWarnings   = enemyWarnings.toList(),
                        isBanTurn       = isBanTurn.value,
                        onHeroSelected  = { hero -> handleHeroSelected(hero) },
                        onHeroLongPress = { /* TODO: [Issue-12] show hero detail popup */ },
                        onMinimize      = { isExpanded.value = false; removePanel() },
                        onClose         = { stopSelf() }
                    )
                }
            }
        }
        windowManager.addView(panelView, params)
    }

    private fun removePanel() { panelView?.let { windowManager.removeView(it) }; panelView = null }

    // ── Session observation ───────────────────────────────────────────────────

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
                val newBans = BanRecommender.rank(
                    availableHeroes = allHeroes,
                    bannedIds       = session.allBannedHeroes.map { it.id }.toSet(),
                    pickedIds       = session.allPickedHeroes.map { it.id }.toSet(),
                    weights         = weights
                )
                banSuggestions.clear()
                banSuggestions.addAll(newBans)

                val comp = CompositionAnalyzer.analyze(session.enemyPickedHeroes)
                enemyWarnings.clear()
                enemyWarnings.addAll(comp.warnings)
            }
            DraftPhase.PICK -> {
                val scored = DraftScorer.rankAll(
                    pool        = allHeroes,
                    alliedPicks = session.ourPickedHeroes,
                    enemyPicks  = session.enemyPickedHeroes,
                    bannedIds   = session.unavailableIds,
                    weights     = weights,
                    currentTurn = session.currentTurn
                )
                recommendations.clear()
                recommendations.addAll(scored.take(10))

                val comp = CompositionAnalyzer.analyze(session.enemyPickedHeroes)
                enemyWarnings.clear()
                enemyWarnings.addAll(comp.warnings)
            }
            else -> {}
        }
    }

    private fun handleHeroSelected(hero: Hero) {
        val s = draftSessionManager.session.value
        when (s.phase) {
            DraftPhase.BAN_ROUND_1, DraftPhase.BAN_ROUND_2 -> {
                val slot = (if (s.phase == DraftPhase.BAN_ROUND_1) s.ourBansR1 else s.ourBansR2)
                    .indexOfFirst { it == null }
                if (slot >= 0) {
                    draftSessionManager.recordOurBan(
                        hero,
                        if (s.phase == DraftPhase.BAN_ROUND_1) 1 else 2,
                        slot
                    )
                }
            }
            DraftPhase.PICK -> {
                val slot = s.ourPicks.indexOfFirst { it == null }
                if (slot >= 0) {
                    val topId = recommendations.firstOrNull()?.hero?.id
                    draftSessionManager.recordOurPick(
                        hero,
                        slot,
                        followedRecommendation = hero.id == topId
                    )
                }
            }
            else -> {}
        }
    }
}
