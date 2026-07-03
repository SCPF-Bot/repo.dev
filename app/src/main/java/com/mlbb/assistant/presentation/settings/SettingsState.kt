package com.mlbb.assistant.presentation.settings

import androidx.compose.runtime.Immutable
import com.mlbb.assistant.capture.AspectRatioPreset
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

    val defaultRank: String           = "6 bans (Epic)",
    val lastSyncedLabel: String       = "Never",
    val overlayGranted: Boolean       = false,
    val accessibilityGranted: Boolean = false,
    val calibrationResult: WeightCalibrator.CalibrationResult? = null,
    val isCalibrating: Boolean        = false,

    val aspectRatioPreset: AspectRatioPreset = AspectRatioPreset.AUTO,
    val banPhaseScreenshotUri: String = "",
    val screenMappingJson: String     = "",

    /**
     * Whether Developer mode is enabled.
     * Controls the [DevLogAlias] launcher icon visibility via
     * [com.mlbb.assistant.utils.DevModeManager].
     * Persisted in SharedPreferences (not DataStore) for synchronous reads at startup.
     * Defaults to `true` so a fresh install shows the log viewer icon.
     */
    val developerModeEnabled: Boolean = true,

    // ── Portrait asset pipeline (hero.main/pick/ban.png) ────────────────────
    val portraitTotalHeroes: Int      = 0,
    val portraitDownloadedCount: Int  = 0,
    val portraitOptimizedCount: Int   = 0,
    val portraitTaskRunning: Boolean  = false,
    val portraitTaskLabel: String     = "",
    val portraitTaskProgress: Float   = 0f, // 0f..1f, only meaningful while portraitTaskRunning
    /** Non-null when the last portrait task failed; cleared when a new task starts. */
    val portraitTaskError: String?    = null
)
