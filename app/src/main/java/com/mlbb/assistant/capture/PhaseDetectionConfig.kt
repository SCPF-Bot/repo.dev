package com.mlbb.assistant.capture

/**
 * Named constants for every magic number used in [PhaseDetector] and
 * [FrameProcessor].  Centralising them here satisfies TD-03 and TD-04 and
 * makes threshold tuning discoverable without reading raw detector code.
 *
 * Colour thresholds were measured from MLBB 1.8 / 1.9 screenshot samples at
 * 1080 × 2400 (FHD+) normalised down to a 360 × 800 analysis frame.
 * Adjust [BAN_BANNER_RED_MIN] / [BAN_BANNER_BLUE_MAX] first when the app
 * misclassifies phases after a MLBB client update.
 */
object PhaseDetectionConfig {

    // ── Phase banner colour thresholds (TD-03) ────────────────────────────────

    /** Minimum red-channel mean for a banner to be classified as a BAN phase. */
    const val BAN_BANNER_RED_MIN: Int = 160

    /** Maximum blue-channel mean for a banner to be classified as a BAN phase. */
    const val BAN_BANNER_BLUE_MAX: Int = 140

    /** Minimum green-channel mean for a banner to be classified as a PICK phase. */
    const val PICK_BANNER_GREEN_MIN: Int = 120

    /** Minimum blue-channel mean for a banner to be classified as a PICK phase. */
    const val PICK_BANNER_BLUE_MIN: Int = 160

    // ── Frame brightness / luminance (TD-04) ──────────────────────────────────

    /**
     * Absolute luminance threshold below which a frame is considered "dark" and
     * unlikely to be an active draft screen.  This is the *raw* Y value from
     * the YCbCr colour model, in [0, 255].
     */
    const val LUMINANCE_DARK_THRESHOLD_RAW: Int = 40

    /**
     * Fraction of the frame's median brightness used to derive a
     * *normalised* dark-frame threshold (TD-04).  A frame is dark when:
     *
     *     mean_luminance < median_luminance * LUMINANCE_NORMALISED_RATIO
     *
     * Using a ratio rather than an absolute value makes the detector robust
     * to HDR / adaptive-brightness displays that shift absolute pixel values.
     */
    const val LUMINANCE_NORMALISED_RATIO: Float = 0.35f

    // ── Capture throttle ──────────────────────────────────────────────────────

    /** Minimum interval between frame-capture invocations during active drafting. */
    const val CAPTURE_THROTTLE_ACTIVE_MS: Long = 500L

    /** Minimum interval between frame-capture invocations when idle / paused. */
    const val CAPTURE_THROTTLE_IDLE_MS: Long = 2_000L

    // ── Portrait matching ─────────────────────────────────────────────────────

    /** dHash similarity threshold below which two images are NOT the same hero. */
    const val DHASH_SIMILARITY_THRESHOLD: Float = 0.85f

    /**
     * Weight applied to the colour-histogram similarity component when
     * combining dHash + histogram scores (TD-08 hybrid).
     *
     * Final score = dHash * (1 - HISTOGRAM_WEIGHT) + histogram * HISTOGRAM_WEIGHT
     */
    const val HISTOGRAM_WEIGHT: Float = 0.30f

    // ── Accessibility watchdog ────────────────────────────────────────────────

    /** How often the service checks that its accessibility permission is still active. */
    const val WATCHDOG_INTERVAL_MS: Long = 30_000L

    // ── Session persistence ───────────────────────────────────────────────────

    /** DataStore preference key that stores the JSON session snapshot. */
    const val PREF_SESSION_SNAPSHOT = "overlay_session_snapshot"

    /** DataStore preference key for the persisted bubble X position. */
    const val PREF_BUBBLE_X = "bubble_pos_x"

    /** DataStore preference key for the persisted bubble Y position. */
    const val PREF_BUBBLE_Y = "bubble_pos_y"
}
