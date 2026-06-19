package com.mlbb.assistant.presentation.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Foreground service that hosts the draft-assistant overlay window.
 *
 * Responsibilities (decomposed from the original God-class per AUDIT §3.2):
 *  - Lifecycle: start/stop the foreground notification
 *  - Window management: delegated to [OverlayWindowManager]
 *  - Recommendation state: delegated to [OverlayStateHolder]
 *
 * Started and stopped via the static helpers [start] / [stop] so callers
 * (OverlayController implementation in OverlayModule) don't hold a direct
 * reference to a concrete service class.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID    = "mlbb_overlay"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Timber.i("OverlayService: started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("OverlayService: stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Draft Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "MLBB draft assistant overlay is active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MLBB Draft Assistant")
            .setContentText("Overlay active — tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
