package com.mlbb.assistant.presentation.settings

import androidx.compose.runtime.Immutable
import com.mlbb.assistant.domain.engine.WeightCalibrator

@Immutable
data class SettingsState(
    val metaWeight: Float             = 0.40f,
    val counterWeight: Float          = 0.30f,
    val synergyWeight: Float          = 0.30f,
    val overlayOpacity: Float         = 0.87f,
    val autoShowOverlay: Boolean      = true,
    val voiceAlertsEnabled: Boolean   = false,
    val autoSync: Boolean             = true,

    /**
     * Ban-count tier: "6 bans (Epic)", "8 bans (Legend)", "10 bans (Mythic and higher)".
     * Controls how many portrait slots the overlay watches during the ban phase.
     */
    val defaultRank: String           = "6 bans (Epic)",
    val lastSyncedLabel: String       = "Never",
    val overlayGranted: Boolean       = false,
    val accessibilityGranted: Boolean = false,
    val calibrationResult: WeightCalibrator.CalibrationResult? = null,
    val isCalibrating: Boolean        = false,

    /**
     * Content URI (as String) of a user-selected screenshot of the banning phase.
     * Empty = no screenshot set.
     */
    val banPhaseScreenshotUri: String = "",

    /**
     * JSON-serialised list of normalised (x, y) tap positions the user mapped onto
     * the ban-phase screenshot. Format: `[{"x":0.25,"y":0.3},…]`.
     * Empty = no mapping saved yet.
     */
    val screenMappingJson: String = ""
)
