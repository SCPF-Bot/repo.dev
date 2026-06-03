package com.mlbbassistant.overlay

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
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
import kotlinx.coroutines.flow.catch
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

    private var initialX = 0; private var initialY = 0
    private var touchX = 0f;  private var touchY = 0f

    companion object { private const val NOTIFICATION_ID = 1001; private const val TAG = "OverlayService" }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf(); return
        }
        inflateOverlay()
        observeSuggestions()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        safeRemoveOverlay()
        super.onDestroy()
    }

    // ── Overlay ──────────────────────────────────────────────────────────────

    private fun inflateOverlay() {
        try {
            val binding = OverlayViewBinding.inflate(LayoutInflater.from(this))
            overlayBinding = binding
            binding.rvOverlaySuggestions.adapter = suggestionAdapter

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 200 }

            setupDrag(binding, params)
            binding.btnCloseOverlay.setOnClickListener { stopSelf() }
            windowManager.addView(binding.root, params)

            lifecycleScope.launch {
                runCatching { binding.root.alpha = userPreferences.overlayOpacity.first() }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW permission missing", e); stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "inflateOverlay failed", e); stopSelf()
        }
    }

    private fun setupDrag(binding: OverlayViewBinding, params: WindowManager.LayoutParams) {
        binding.overlayHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX;  touchY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(binding.root, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - touchX) < 10f && abs(event.rawY - touchY) < 10f) {
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

    private fun safeRemoveOverlay() {
        val view = overlayBinding?.root ?: return
        try {
            if (view.windowToken != null) windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed (already removed?)", e)
        } finally {
            overlayBinding = null
        }
    }

    // ── Suggestions ──────────────────────────────────────────────────────────

    private fun observeSuggestions() {
        lifecycleScope.launch {
            val configFlow = combine(
                userPreferences.suggestionCount,
                userPreferences.weightMeta,
                userPreferences.weightCounter,
                userPreferences.weightSynergy
            ) { topN, wM, wC, wS -> DraftEngine.Weights(wM, wC, wS) to topN }
                .catch { emit(DraftEngine.Weights() to 5) }

            combine(heroRepository.observeHeroes(), configFlow) { heroes, (weights, topN) ->
                runCatching { draftEngine.suggest(heroes, DraftState(), topN, weights) }
                    .getOrDefault(emptyList())
            }
                .catch { emit(emptyList()) }
                .collect { suggestions ->
                    runCatching { suggestionAdapter.submitList(suggestions) }
                }
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(tap)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
