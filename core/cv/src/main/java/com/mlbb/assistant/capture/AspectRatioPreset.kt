package com.mlbb.assistant.capture

/**
 * Screen aspect-ratio presets for the MLBB draft-capture pipeline.
 *
 * MLBB's draft UI is calibrated at 20:9 (1600 × 720 px reference). On devices
 * with different aspect ratios the game engine may letterbox or pillarbox the
 * game content, shifting the position of ban/pick slots relative to the raw
 * screen edges. Selecting the correct preset lets the CV pipeline apply the
 * right coordinate offsets so it looks in the right place.
 *
 * --- How it is used ---
 * [SlotRegions] stores normalised coordinates calibrated for MLBB's native
 * 20:9 layout. [effectiveRatio] exposes the actual (or inferred) width-to-
 * height ratio so callers can compute a horizontal content inset when needed.
 * When [isAutoDetect] is true the pipeline measures the captured frame at
 * runtime and picks the closest preset automatically.
 *
 * --- User-facing language ---
 * [label] is the short pill-button text (e.g. "Standard").
 * [friendlyName] is the expanded name shown in the section title area.
 * [description] is a one-sentence hint displayed below the selector, written
 * for players who are not familiar with aspect-ratio numbers.
 */
enum class AspectRatioPreset(
    /** Stable DataStore value — never rename. */
    val key: String,
    /** Short text for the pill button (≤ 12 chars). */
    val label: String,
    /** Expanded human-readable name. */
    val friendlyName: String,
    /** Non-technical one-line description shown below the selector. */
    val description: String,
    /**
     * The landscape width-to-height ratio for this preset, or `null` when
     * [isAutoDetect] is true (ratio is measured from the live frame instead).
     *
     * Reference values:
     *  • 16:9  ≈ 1.778  — classic widescreen
     *  • 20:9  ≈ 2.222  — MLBB calibration reference (Snapdragon mid-range common)
     *  • 21:9  ≈ 2.333  — Sony Xperia and some ultra-wide flagships
     */
    val widthToHeightRatio: Float?
) {

    /**
     * Let the app figure out the screen shape automatically.
     * Recommended for almost all players — picks the closest preset at runtime.
     */
    AUTO(
        key              = "auto",
        label            = "Auto",
        friendlyName     = "Auto (recommended)",
        description      = "The app detects your screen shape automatically. Works for most players — leave this on unless you see misaligned overlays.",
        widthToHeightRatio = null
    ),

    /**
     * Standard widescreen found on the vast majority of mid-range Android phones
     * (Samsung Galaxy A/S series, Xiaomi, OPPO, Vivo, Realme, most Huawei, etc.).
     */
    STANDARD_16_9(
        key              = "16_9",
        label            = "Standard",
        friendlyName     = "Standard (most phones)",
        description      = "Choose this if Auto isn't working right. Fits Samsung Galaxy, Xiaomi, OPPO, Vivo, and most other Android phones.",
        widthToHeightRatio = 16f / 9f
    ),

    /**
     * Ultra-wide "cinema" screen found on Sony Xperia phones and a handful of
     * other slim-form-factor flagships. The extra screen real estate sits on
     * the left and right of MLBB's game window.
     */
    ULTRAWIDE_21_9(
        key              = "21_9",
        label            = "Widescreen",
        friendlyName     = "Widescreen (ultra-wide phones)",
        description      = "For phones with a very long, narrow screen — mainly Sony Xperia and some LG models. The game has black bars on the sides.",
        widthToHeightRatio = 21f / 9f
    );

    /** True when the ratio is measured at runtime rather than fixed by this preset. */
    val isAutoDetect: Boolean get() = widthToHeightRatio == null

    /**
     * Resolves the effective ratio, falling back to [STANDARD_16_9] when this
     * preset uses auto-detect and no runtime measurement is available.
     */
    fun effectiveRatio(runtimeRatio: Float? = null): Float =
        widthToHeightRatio ?: runtimeRatio ?: STANDARD_16_9.widthToHeightRatio!!

    companion object {
        /** Looks up a preset by its [key]. Returns [AUTO] for unknown values. */
        fun fromKey(key: String): AspectRatioPreset =
            entries.firstOrNull { it.key == key } ?: AUTO

        /**
         * Snaps a measured width-to-height ratio to the nearest preset.
         * Used by the auto-detect path when [AUTO] is selected.
         */
        fun nearest(ratio: Float): AspectRatioPreset = when {
            ratio >= (ULTRAWIDE_21_9.widthToHeightRatio!! + STANDARD_16_9.widthToHeightRatio!!) / 2 -> ULTRAWIDE_21_9
            else -> STANDARD_16_9
        }
    }
}
