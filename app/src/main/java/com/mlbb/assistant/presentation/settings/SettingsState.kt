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

    val defaultRank: String           = "6 bans (Epic)",
    val overlayGranted: Boolean       = false,
    val accessibilityGranted: Boolean = false,
    val calibrationResult: WeightCalibrator.CalibrationResult? = null,
    val isCalibrating: Boolean        = false,

    val aspectRatioPreset: AspectRatioPreset = AspectRatioPreset.AUTO,

    /**
     * Whether the ML Kit OCR phase-detection cross-check ([com.mlbb.assistant.capture.PhaseOcrDetector])
     * runs alongside the colour-based [com.mlbb.assistant.capture.PhaseDetector].
     * Defaults to `true`. Disabling it avoids ML Kit's one-time on-device model
     * download (see `docs/misc.md` §14) at the cost of losing OCR's edge-case
     * disambiguation — the colour detector remains fully functional either way.
     */
    val enableOcrPhaseDetection: Boolean = true,

    /**
     * Whether Developer mode is enabled.
     * Controls the [DevLogAlias] launcher icon visibility via
     * [com.mlbb.assistant.utils.DevModeManager].
     * Persisted in SharedPreferences (not DataStore) for synchronous reads at startup.
     * Defaults to `true` so a fresh install shows the log viewer icon.
     */
    val developerModeEnabled: Boolean = true,
)
