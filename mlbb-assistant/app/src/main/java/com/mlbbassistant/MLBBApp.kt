package com.mlbbassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.mlbbassistant.data.repository.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MLBBApp : Application() {

    @Inject lateinit var databaseInitializer: DatabaseInitializer

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        databaseInitializer.seedIfEmpty()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            "MLBB Assistant Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while the MLBB draft assistant overlay is active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val OVERLAY_CHANNEL_ID = "overlay_service_channel"
    }
}
