package com.mlbb.assistant.capture

/**
 * §5 — Local kill-switch for the CV matching cascade (`todo.md` §5, "Feature-flag
 * gate for the CV matching cascade").
 *
 * This is intentionally a plain in-process object, not a remote-config client —
 * the project has no remote-config plumbing (see [PhaseDetectionConfig.USE_SLOT_AWARE_HASH]
 * KDoc, which this supersedes). It exists so a bad `hero_thresholds.json`
 * calibration or a [SlotAwareHasher] regression can be rolled back **without a
 * rebuild**: flip the flag via [set] (e.g. wired to a hidden Settings toggle or
 * [com.mlbb.assistant.utils.DevModeManager]) and the change takes effect on the
 * next [PortraitMatcher.match] call.
 *
 * Flags default to the same values [PhaseDetectionConfig] previously hardcoded,
 * so behaviour is unchanged until a flag is explicitly flipped.
 *
 * A full remote-config-backed implementation (Firebase Remote Config or similar)
 * is a larger follow-up tracked in `roadmap.md` — this local switch unblocks the
 * "roll back a bad calibration in production" use case in the meantime.
 */
object CvFeatureFlags {

    /** Master switch for the [SlotAwareHasher] triple-hash fusion path. */
    @Volatile
    var useSlotAwareHash: Boolean = PhaseDetectionConfig.USE_SLOT_AWARE_HASH
        private set

    /**
     * Enables the rolling-window plurality-vote [SlotConsensusManager] path inside
     * [PortraitMatcher.match]. When disabled, only the stricter consecutive-hit
     * counter ([PortraitMatcher] `applyConfirmation`) gates confirmation.
     */
    @Volatile
    var enableTemporalConsensus: Boolean = true
        private set

    /**
     * Enables the legacy pHash + colour-histogram fallback path (tier 3). When
     * disabled, [PortraitMatcher.match] returns an unmatched [MatchResult] if
     * both the TFLite classifier and slot-aware hash fusion fail to produce a
     * confident result, instead of falling through to pHash.
     */
    @Volatile
    var tfliteFallbackEnabled: Boolean = true
        private set

    /**
     * Enables the [PhaseOcrDetector] cross-check inside [com.mlbb.assistant.presentation.overlay.OverlayCaptureCoordinator].
     * When disabled, phase detection relies solely on [PhaseDetector]'s colour
     * heuristics. This is a network/battery trade-off, not a correctness one:
     * ML Kit's on-device Text Recognition model downloads lazily on first use
     * (see `docs/misc.md` §14), so a user on a metered/limited connection can
     * opt out of that one-time download entirely by disabling this flag.
     * Wired to the "OCR phase detection" toggle in Settings.
     */
    @Volatile
    var enableOcr: Boolean = true
        private set

    /** Resets every flag to its default (all cascade tiers enabled). */
    fun resetToDefaults() {
        useSlotAwareHash = PhaseDetectionConfig.USE_SLOT_AWARE_HASH
        enableTemporalConsensus = true
        tfliteFallbackEnabled = true
        enableOcr = true
    }

    fun setUseSlotAwareHash(enabled: Boolean) {
        useSlotAwareHash = enabled
    }

    fun setEnableTemporalConsensus(enabled: Boolean) {
        enableTemporalConsensus = enabled
    }

    fun setTfliteFallbackEnabled(enabled: Boolean) {
        tfliteFallbackEnabled = enabled
    }

    fun setEnableOcr(enabled: Boolean) {
        enableOcr = enabled
    }
}
