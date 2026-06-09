package com.mlbb.assistant.presentation.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetSuggestionsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject
    lateinit var getHeroesUseCase: GetHeroesUseCase

    @Inject
    lateinit var getSuggestionsUseCase: GetSuggestionsUseCase

    // --- Lifecycle + SavedState owners required by ComposeView in a Service ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView

    // Single coroutine scope tied to the service; cancelled in onDestroy
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var allHeroes: List<Hero> = emptyList()
    private var lastSuggestion: Pair<Hero, Double>? by mutableStateOf(null)

    override fun onCreate() {
        // Initialise SavedState + Lifecycle before super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
        startForegroundService()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startUpdatingSuggestions()
    }

    private fun createOverlayView() {
        overlayView = ComposeView(this).apply {
            // Wire lifecycle and saved-state owners so Compose can attach
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                MaterialTheme {
                    SuggestionOverlayContent(
                        hero = lastSuggestion?.first,
                        score = lastSuggestion?.second
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 100
        }
        windowManager.addView(overlayView, params)
    }

    private fun startForegroundService() {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MLBB Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MLBB Assistant")
            .setContentText("Overlay is active")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()
        startForeground(1, notification)
    }

    private fun startUpdatingSuggestions() {
        // Collect heroes on IO, then periodically refresh suggestion on Main
        serviceScope.launch(Dispatchers.IO) {
            getHeroesUseCase().collect { heroes ->
                allHeroes = heroes
                updateSuggestion()
            }
        }
        // Periodic refresh every 2 s — entirely inside a coroutine, not blocking
        serviceScope.launch {
            while (isActive) {
                delay(2000)
                updateSuggestion()
            }
        }
    }

    private suspend fun updateSuggestion() {
        if (allHeroes.isEmpty()) return
        val suggestions = getSuggestionsUseCase(
            allHeroes = allHeroes,
            allies = emptyList(),
            enemies = emptyList(),
            weights = ScoreWeights(0.5, 0.3, 0.2),
            bannedIds = emptyList()
        )
        // Update Compose state on Main thread — triggers recomposition automatically
        withContext(Dispatchers.Main) {
            lastSuggestion = suggestions.firstOrNull()
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        serviceScope.cancel()

        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun SuggestionOverlayContent(hero: Hero?, score: Double?) {
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (hero != null) {
                Text(text = "Top Pick: ${hero.name}", color = Color.White, fontSize = 16.sp)
                Text(
                    text = "Score: ${String.format("%.2f", score ?: 0.0)}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            } else {
                Text(text = "No suggestion", color = Color.White)
            }
        }
    }
}
