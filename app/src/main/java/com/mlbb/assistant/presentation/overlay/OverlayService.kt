package com.mlbb.assistant.presentation.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var getHeroesUseCase: GetHeroesUseCase
    @Inject lateinit var getSuggestionsUseCase: GetSuggestionsUseCase

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val heroListRef = AtomicReference<List<Hero>>(emptyList())
    private var lastSuggestion: Pair<Hero, Double>? by mutableStateOf(null)

    override fun onCreate() {
        // SavedStateRegistryController must be attached and restored before super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        // Lifecycle events dispatched after super.onCreate() per LifecycleOwner contract
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // startForeground must be called ASAP on API 31+ to avoid ANR/StopForegroundException
        startServiceForeground()

        createOverlayView()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startUpdatingSuggestions()
    }

    private fun startServiceForeground() {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "MLBB Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MLBB Assistant")
            .setContentText("Overlay is active")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createOverlayView() {
        overlayView = ComposeView(this).apply {
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
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 100
        }
        windowManager.addView(overlayView, params)
    }

    private fun startUpdatingSuggestions() {
        serviceScope.launch(Dispatchers.IO) {
            getHeroesUseCase().collect { heroes ->
                heroListRef.set(heroes)
                updateSuggestion()
            }
        }
        // Periodic refresh — scoring runs on Default to avoid blocking Main
        serviceScope.launch {
            while (isActive) {
                delay(2000)
                updateSuggestion()
            }
        }
    }

    private suspend fun updateSuggestion() {
        val heroes = heroListRef.get()
        if (heroes.isEmpty()) return
        // Compute on Default dispatcher — potentially expensive with large hero lists
        val suggestion = withContext(Dispatchers.Default) {
            getSuggestionsUseCase(
                allHeroes = heroes,
                allies = emptyList(),
                enemies = emptyList(),
                weights = ScoreWeights(0.5, 0.3, 0.2),
                bannedIds = emptyList()
            ).firstOrNull()
        }
        withContext(Dispatchers.Main) {
            lastSuggestion = suggestion
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        // Guard against removeView on a view that was never successfully added
        if (::overlayView.isInitialized) {
            try { windowManager.removeView(overlayView) } catch (_: IllegalArgumentException) { }
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
                    text = "Score: ${String.format(Locale.US, "%.2f", score ?: 0.0)}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            } else {
                Text(text = "No suggestion", color = Color.White)
            }
        }
    }
}
