package com.mlbbassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.mlbbassistant.core.GlobalExceptionHandler
import com.mlbbassistant.data.repository.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MLBBApp : Application() {

    @Inject lateinit var databaseInitializer: DatabaseInitializer

    override fun onCreate() {
        super.onCreate()
        installGlobalExceptionHandler()
        createNotificationChannels()
        safeSeed()
    }

    private fun installGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(this, defaultHandler)
        )
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            "MLBB Assistant Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while the draft assistant overlay is active"
            setShowBadge(false)
        }
        try {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channel", e)
        }
    }

    private fun safeSeed() {
        try {
            databaseInitializer.seedIfEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Database seed failed — app will still work with in-memory data", e)
        }
    }

    companion object {
        const val OVERLAY_CHANNEL_ID = "overlay_service_channel"
        private const val TAG = "MLBBApp"
    }
}
