package com.mlbb.assistant.presentation.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings as SystemSettings
import androidx.core.app.NotificationCompat
import coil3.ImageLoader
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.yazanaesmael.jetoverlay.JetOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that drives the MLBB Draft Assistant overlay.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * JetOverlay integration (rec. §1.1 — Critical)
 * ─────────────────────────────────────────────────────────────────────────────
 * JetOverlay owns the [WindowManager] window, drag physics, and lifecycle for
 * the overlay composable. This service has been reduced from ~1,100 LOC to
 * ~200 LOC by removing:
 *   • WindowManager.addView / removeView / updateViewLayout
 *   • The dual-mode setOnTouchListener (bubble + widget mode, ~150 LOC)
 *   • LifecycleRegistry + SavedStateRegistryController boilerplate
 *   • WindowManager.LayoutParams helpers (buildBubbleParams, bubbleFlags, etc.)
 *   • expand / collapse window-param mutations
 *
 * Responsibilities that remain here:
 *   1. FGS lifecycle: startFg() / upgradeFgToProjection()
 *   2. Permission watchdog: stop self if SYSTEM_ALERT_WINDOW is revoked
 *   3. Capture delegation: forward projection token to OverlayCaptureCoordinator
 *   4. Bridge wiring: populate OverlayContentBridge before JetOverlay.show()
 *
 * All state, scoring, and manual controls live in [OverlayStateHolder].
 * All CV frame analysis lives in [OverlayCaptureCoordinator].
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Note — TD-12 (bubble position persistence): JetOverlay persists drag position
 * internally. The custom DataStore-based position save/restore is no longer
 * needed and has been removed. If fine-grained position control is required in
 * future, hook into JetOverlay's onDragEnd callback.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    // ── Hilt injections ───────────────────────────────────────────────────────
    @Inject lateinit var stateHolder:         OverlayStateHolder
    @Inject lateinit var captureCoordinator:  OverlayCaptureCoordinator
    @Inject lateinit var draftSessionManager: DraftSessionManager
    @Inject lateinit var imageLoader:         ImageLoader

    // ── Service coroutine scope ───────────────────────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Companion ─────────────────────────────────────────────────────────────
    companion object {
        private const val NOTIF_CHANNEL        = "overlay_channel"
        private const val NOTIF_ID             = 1
        const val EXTRA_RESULT_CODE            = "extra_result_code"
        const val EXTRA_PROJECTION_DATA        = "extra_projection_data"
        const val ACTION_RELAUNCH_OVERLAY      = "com.mlbb.assistant.ACTION_RELAUNCH_OVERLAY"

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

        // 1. Wire the bridge BEFORE JetOverlay.show() so DraftOverlayContent
        //    can read stateHolder and draftSessionManager safely.
        OverlayContentBridge.holder              = stateHolder
        OverlayContentBridge.draftSessionManager = draftSessionManager
        OverlayContentBridge.stopServiceCallback = { stopSelf() }

        // 2. Initialise capture dependencies (ScreenCaptureManager + PortraitMatcher).
        captureCoordinator.init(imageLoader)

        // 3. Start hero loading, session observation, and scoring config watchers.
        stateHolder.start(serviceScope, captureCoordinator)

        // 4. Start the foreground notification.
        createNotificationChannel()
        startFg()

        // 5. Show the floating overlay window via JetOverlay.
        //    DraftOverlayContent was registered in MLBBApplication.onCreate();
        //    show() inflates it inside JetOverlay's WindowManager window.
        JetOverlay.show()

        // 6. Overlay permission watchdog.
        startPermissionWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Guard: if permission was revoked while stopped, bail out immediately.
        if (!SystemSettings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Handle "Relaunch Overlay" notification tap.
        if (intent?.action == ACTION_RELAUNCH_OVERLAY) {
            if (!stateHolder.isExpanded.value) stateHolder.isExpanded.value = true
            JetOverlay.show()
            return START_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        @Suppress("DEPRECATION")
        val projData: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        else
            intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)

        if (resultCode != -1 && projData != null) {
            upgradeFgToProjection()
            captureCoordinator.startCapture(resultCode, projData, serviceScope)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        captureCoordinator.stop()
        stateHolder.stop()
        serviceScope.cancel()

        // Hide the overlay window before the service is torn down.
        JetOverlay.hide()

        // Clear bridge references to avoid leaking service context.
        OverlayContentBridge.holder              = null
        OverlayContentBridge.draftSessionManager = null
        OverlayContentBridge.stopServiceCallback = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Foreground service notification ───────────────────────────────────────

    /**
     * Two-step FGS start:
     * 1. onCreate → SPECIAL_USE only (safe before projection token is obtained).
     * 2. onStartCommand (with token) → adds MEDIA_PROJECTION via upgradeFgToProjection().
     *
     * Doing both in step 1 on Android 14+ causes a SecurityException.
     */
    private fun startFg() {
        val notif = buildNotification()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else ->
                startForeground(NOTIF_ID, notif)
        }
    }

    private fun upgradeFgToProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
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

    // ── Permission watchdog ───────────────────────────────────────────────────

    /**
     * Checks every 30 s that SYSTEM_ALERT_WINDOW is still granted.
     * If revoked while running, the service stops itself gracefully rather than
     * crashing on the next WindowManager call inside JetOverlay.
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
}
