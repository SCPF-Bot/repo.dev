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

    // ── HSV phase detection thresholds ────────────────────────────────────────

    /**
     * HSV hue range for the MLBB ban-phase red accent (both ends of the hue circle).
     * Android [android.graphics.Color.colorToHSV] returns hue in [0, 360).
     * Red occupies 0–20° and 340–360°.
     */
    const val BAN_HUE_LOW:  Float = 20f
    const val BAN_HUE_HIGH: Float = 340f   // red wraps: hue < LOW or hue > HIGH

    /**
     * HSV hue range for the MLBB pick-phase blue/teal accent.
     * Teal/cyan sits around 180–220°.
     */
    const val PICK_HUE_LOW:  Float = 170f
    const val PICK_HUE_HIGH: Float = 240f

    /** Minimum HSV saturation for a pixel to count as a phase-colour sample. */
    const val HSV_SATURATION_MIN: Float = 0.40f

    /** Minimum HSV value (brightness) for a pixel to count as a phase-colour sample. */
    const val HSV_VALUE_MIN: Float = 0.35f

    /** Fraction of sampled pixels that must satisfy HSV criteria to declare a phase. */
    const val HSV_PHASE_RATIO_THRESHOLD: Float = 0.08f

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

    // ── Slot-fill saturation check ────────────────────────────────────────────

    /**
     * Minimum average HSV saturation for a slot crop to be considered "filled".
     * Empty MLBB ban/pick slots are drawn with a near-grey circular border —
     * saturation is < 0.15. A hero portrait has saturation > 0.20 on average.
     */
    const val SLOT_SATURATION_FILLED_MIN: Float = 0.18f

    /**
     * Minimum colour variance (per-pixel squared deviation from mean luminance)
     * for a slot crop to count as filled. Pure-grey empty slots have variance ≈ 0.
     */
    const val SLOT_VARIANCE_FILLED_MIN: Float = 200f

    // ── Capture throttle ──────────────────────────────────────────────────────

    /**
     * Minimum interval between frame-capture invocations during active drafting.
     * Reduced from 500ms to 250ms — a 30-second pick clock has frames at 4 Hz,
     * giving enough temporal resolution to catch fast hero-lock animations.
     */
    const val CAPTURE_THROTTLE_ACTIVE_MS: Long = 250L

    /** Minimum interval between frame-capture invocations when idle / paused. */
    const val CAPTURE_THROTTLE_IDLE_MS: Long = 2_000L

    // ── Portrait matching ─────────────────────────────────────────────────────

    /**
     * dHash similarity threshold below which two images are NOT the same hero.
     * Used as a fast pre-filter before the more expensive pHash + histogram.
     */
    const val DHASH_SIMILARITY_THRESHOLD: Float = 0.72f

    /**
     * pHash similarity threshold for the primary matching score.
     * pHash (DCT-based) is more accurate than dHash under brightness/compression variation.
     */
    const val PHASH_SIMILARITY_THRESHOLD: Float = 0.82f

    /**
     * Weight applied to the colour-histogram similarity component when
     * combining pHash + histogram scores.
     */
    const val HISTOGRAM_WEIGHT: Float = 0.30f

    /**
     * Number of consecutive frames that must agree on the same hero before it is
     * recorded as a confirmed pick or ban. Prevents one-frame false positives from
     * animation frames (hero reveal fly-in, hover glow effects, etc.).
     */
    const val CONFIRMATION_FRAMES_REQUIRED: Int = 2

    /**
     * Number of frames between OCR phase detection runs. OCR costs ~30ms so
     * running every frame would add latency on slow devices.
     * At 250ms polling, every 4th frame = ~1 s between OCR checks.
     */
    const val OCR_FRAME_STRIDE: Int = 4

    /**
     * Minimum OCR confidence for the OCR result to override the colour-based
     * phase result. [PhaseOcrDetector] returns 0.90 for explicit keyword matches.
     */
    const val OCR_OVERRIDE_CONFIDENCE: Float = 0.70f

    // ── TFLite hero classifier (HeroClassifier / misc.md §13) ────────────────

    /**
     * Minimum softmax confidence from [HeroClassifier] for a result to be used
     * directly, bypassing the pHash + histogram fallback.
     *
     * Tuned conservatively at 0.70: MobileNetV3Small reaches ~0.90 on clean crops
     * but drops to ~0.60–0.65 on hero-reveal animation frames. The threshold is set
     * below the steady-state confidence to allow the classifier to fire on the first
     * clear frame while still rejecting ambiguous animation frames.
     * Lower this value carefully — it increases the false-positive rate.
     */
    const val TFLITE_ACCEPT_THRESHOLD: Float = 0.70f

    /**
     * Minimum softmax confidence for the TFLite result to be returned as a
     * "requires confirmation" match (confident enough to start the multi-frame
     * counter, but not confident enough to record immediately).
     * Must be less than [TFLITE_ACCEPT_THRESHOLD].
     */
    const val TFLITE_TENTATIVE_THRESHOLD: Float = 0.45f

    /**
     * Maximum number of top-K predictions requested from [HeroClassifier.classify].
     * Only the top-1 result drives the matching logic; top-2/3 are logged for
     * debugging and future ensemble scoring work.
     */
    const val TFLITE_TOP_K: Int = 3

    // ── Phase history smoothing ───────────────────────────────────────────────

    /**
     * Number of recent phase-detection results used to smooth phase transitions.
     * A phase is only acted on when it appears in the majority of the history window.
     */
    const val PHASE_HISTORY_SIZE: Int = 3

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
