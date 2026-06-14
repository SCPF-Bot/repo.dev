package com.mlbb.assistant.presentation.settings

data class SettingsState(
    val metaWeight: Float    = 0.40f,
    val counterWeight: Float = 0.30f,
    val synergyWeight: Float = 0.30f,
    val overlayOpacity: Float   = 0.87f,
    val autoShowOverlay: Boolean = true,
    val voiceAlertsEnabled: Boolean = false,
    val autoSync: Boolean    = true,
    val defaultRank: String  = "Epic",
    val lastSyncedLabel: String = "Never",
    val overlayGranted: Boolean      = false,
    val accessibilityGranted: Boolean = false
)
