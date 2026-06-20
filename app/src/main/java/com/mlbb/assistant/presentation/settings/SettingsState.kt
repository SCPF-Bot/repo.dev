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
    val defaultRank: String           = "Epic",
    val lastSyncedLabel: String       = "Never",
    val overlayGranted: Boolean       = false,
    val accessibilityGranted: Boolean = false,

    /**
     * Section 5.2.2 — Calibration transparency.
     *
     * Non-null when [WeightCalibrator] has enough history (≥ 10 labelled
     * sessions) to suggest weight adjustments.  The UI shows the suggested
     * weights, confidence level, and rationale text so users understand why
     * the engine recommends changing the weights.
     */
    val calibrationResult: WeightCalibrator.CalibrationResult? = null,

    /** True while the background calibration computation is running. */
    val isCalibrating: Boolean = false
)
