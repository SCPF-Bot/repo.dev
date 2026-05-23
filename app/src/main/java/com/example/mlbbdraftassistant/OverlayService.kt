package com.example.mlbbdraftassistant

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.mlbbdraftassistant.service.GameAccessibilityService
import com.example.mlbbdraftassistant.ui.overlay.DetectionMode
import com.example.mlbbdraftassistant.ui.overlay.DraftViewModel
import com.example.mlbbdraftassistant.ui.overlay.OverlayContent
import com.example.mlbbdraftassistant.ui.theme.MLBBDraftTheme
import com.example.mlbbdraftassistant.util.PrefKeys

class OverlayService : Service(), ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var viewModel: DraftViewModel
    private val stopReceiver = StopReceiver()
    private val draftReceiver = DraftEventReceiver()
    private var autoCaptureEnabled = false
    private lateinit var layoutParams: WindowManager.LayoutParams

    // ViewModelStoreOwner
    private val viewModelStore = ViewModelStore()
    override val viewModelStoreOwner: ViewModelStore get() = this
    override fun getViewModelStore(): ViewModelStore = viewModelStore

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_channel"
        const val ACTION_STOP = "com.example.mlbbdraftassistant.STOP_SERVICE"
        const val ACTION_SET_MEDIA_PROJECTION = "com.example.mlbbdraftassistant.SET_MEDIA_PROJECTION"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        autoCaptureEnabled = prefs.getBoolean(PrefKeys.AUTO_CAPTURE, false)

        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), RECEIVER_NOT_EXPORTED)
        registerReceiver(draftReceiver, IntentFilter().apply {
            addAction(GameAccessibilityService.ACTION_DRAFT_ENTERED)
            addAction(GameAccessibilityService.ACTION_DRAFT_EXITED)
        }, RECEIVER_NOT_EXPORTED)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        viewModel = ViewModelProvider(this).get(DraftViewModel::class.java)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(this).apply {
            setContent {
                MLBBDraftTheme(darkTheme = true) {
                    val state by viewModel.state.collectAsState()
                    OverlayContent(
                        state = state,
                        autoCaptureEnabled = autoCaptureEnabled,
                        onAllySelected = { slot, hero -> viewModel.setAlly(slot, hero) },
                        onEnemySelected = { slot, hero -> viewModel.setEnemy(slot, hero) },
                        onReset = { viewModel.resetDraft() },
                        onLockToggle = { viewModel.toggleLock() },
                        onCapture = { viewModel.detectDraft() },
                        onToggleDetectionMode = {
                            val newMode = if (state.detectionMode == DetectionMode.OCR) {
                                DetectionMode.ICON
                            } else {
                                DetectionMode.OCR
                            }
                            viewModel.setDetectionMode(newMode)
                        },
                        onOpenCalibration = {
                            startActivity(
                                Intent(this@OverlayService, CalibrationActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                        onOpenSettings = {
                            startActivity(
                                Intent(this@OverlayService, SettingsActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    )
                }
            }
        }

        val opacity = prefs.getFloat(PrefKeys.OVERLAY_OPACITY, 0.85f)

        layoutParams = WindowManager.LayoutParams(
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
            x = 100
            y = 200
            alpha = opacity
        }

        windowManager.addView(composeView, layoutParams)
        enableDrag(composeView, layoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_MEDIA_PROJECTION) {
            val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>("data")
            if (resultCode == Activity.RESULT_OK && data != null) {
                val manager = getSystemService(MediaProjectionManager::class.java)
                val projection = manager.getMediaProjection(resultCode, data)
                viewModel.captureManager.setMediaProjection(projection)
            }
        }
        return START_STICKY
    }

    private fun enableDrag(view: android.view.View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the MLBB Draft Assistant overlay running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Draft Assistant Active")
            .setContentText("Draft detection ready")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private inner class StopReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopSelf()
        }
    }

    private inner class DraftEventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                GameAccessibilityService.ACTION_DRAFT_ENTERED -> {
                    if (autoCaptureEnabled && viewModel.captureManager.isReady()) {
                        viewModel.detectDraft()
                    }
                }
                GameAccessibilityService.ACTION_DRAFT_EXITED -> { }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stopReceiver)
        unregisterReceiver(draftReceiver)
        viewModelStore.clear()
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }
}