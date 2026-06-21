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
    val isCalibrating: Boolean = false,

    /**
     * Content URI (as String) of a user-selected screenshot of the banning
     * phase.  The app uses this as a pixel-map reference for rank-aware
     * portrait matching.  Empty string means no screenshot has been set.
     */
    val banPhaseScreenshotUri: String = "",

    /**
     * Content URI (as String) of a user-selected JSON file containing text
     * descriptions for each score level.  The overlay widget reads this file
     * to display human-readable score summaries.  Empty string means the
     * built-in default descriptions are used.
     */
    val scoreDescriptionsJsonUri: String = ""
)
