package com.mlbbassistant.overlay

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mlbbassistant.MLBBApp.Companion.OVERLAY_CHANNEL_ID
import com.mlbbassistant.R
import com.mlbbassistant.core.DraftEngine
import com.mlbbassistant.data.model.DraftState
import com.mlbbassistant.data.repository.HeroRepository
import com.mlbbassistant.data.repository.UserPreferences
import com.mlbbassistant.databinding.OverlayViewBinding
import com.mlbbassistant.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class OverlayService : LifecycleService() {

    @Inject lateinit var heroRepository: HeroRepository
    @Inject lateinit var draftEngine: DraftEngine
    @Inject lateinit var userPreferences: UserPreferences

    private lateinit var windowManager: WindowManager
    private var overlayBinding: OverlayViewBinding? = null
    private val suggestionAdapter = OverlaySuggestionAdapter()

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        inflateOverlay()
        observeSuggestions()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        overlayBinding?.root?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        overlayBinding = null
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Overlay inflation
    // ─────────────────────────────────────────────────────────────────────────

    private fun inflateOverlay() {
        val binding = OverlayViewBinding.inflate(LayoutInflater.from(this))
        overlayBinding = binding

        binding.rvOverlaySuggestions.adapter = suggestionAdapter

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        setupDrag(binding, params)
        binding.btnCloseOverlay.setOnClickListener { stopSelf() }
        windowManager.addView(binding.root, params)

        lifecycleScope.launch {
            binding.root.alpha = userPreferences.overlayOpacity.first()
        }
    }

    private fun setupDrag(binding: OverlayViewBinding, params: WindowManager.LayoutParams) {
        binding.overlayHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(binding.root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val movedX = abs(event.rawX - initialTouchX)
                    val movedY = abs(event.rawY - initialTouchY)
                    if (movedX < 10f && movedY < 10f) {
                        // Treat as tap → toggle list visibility
                        binding.rvOverlaySuggestions.visibility =
                            if (binding.rvOverlaySuggestions.visibility == View.VISIBLE)
                                View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Suggestions
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeSuggestions() {
        lifecycleScope.launch {
            val configFlow = combine(
                userPreferences.suggestionCount,
                userPreferences.weightMeta,
                userPreferences.weightCounter,
                userPreferences.weightSynergy
            ) { topN, wMeta, wCounter, wSynergy ->
                DraftEngine.Weights(wMeta, wCounter, wSynergy) to topN
            }

            combine(
                heroRepository.observeHeroes(),
                configFlow
            ) { heroes, (weights, topN) ->
                draftEngine.suggest(heroes, DraftState(), topN, weights)
            }.collect { suggestions ->
                suggestionAdapter.submitList(suggestions)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
