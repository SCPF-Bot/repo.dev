package com.mlbb.assistant.presentation.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.ScoreWeights
import com.mlbb.assistant.domain.usecase.GetHeroesUseCase
import com.mlbb.assistant.domain.usecase.GetSuggestionsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject
    lateinit var getHeroesUseCase: GetHeroesUseCase

    @Inject
    lateinit var getSuggestionsUseCase: GetSuggestionsUseCase

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private var serviceJob: Job? = null
    private var allHeroes: List<Hero> = emptyList()
    private var lastSuggestion: Pair<Hero, Double>? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
        startForegroundService()
        startUpdatingSuggestions()
    }

    private fun createOverlayView() {
        overlayView = ComposeView(this)
        overlayView.setContent {
            MaterialTheme {
                SuggestionOverlayContent(
                    hero = lastSuggestion?.first,
                    score = lastSuggestion?.second
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
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
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            getHeroesUseCase().collect { heroes ->
                allHeroes = heroes
                updateSuggestion()
            }
        }
        while (true) {
            delay(2000)
            updateSuggestion()
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
        lastSuggestion = suggestions.firstOrNull()
        withContext(Dispatchers.Main) {
            overlayView.setContent {
                MaterialTheme {
                    SuggestionOverlayContent(
                        hero = lastSuggestion?.first,
                        score = lastSuggestion?.second
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun SuggestionOverlayContent(hero: Hero?, score: Double?) {
    Card(
        modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (hero != null) {
                Text(text = "Top Pick: ${hero.name}", color = Color.White, fontSize = 16.sp)
                Text(text = "Score: ${String.format("%.2f", score ?: 0.0)}", color = Color.White, fontSize = 12.sp)
            } else {
                Text(text = "No suggestion", color = Color.White)
            }
        }
    }
}