package com.mlbb.assistant.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mlbb.assistant.capture.PhaseDetector.DetectedPhase

/**
 * Section 3.2.4 — Phase disambiguation via ML Kit On-Device OCR.
 *
 * [PhaseDetector] uses colour histogram heuristics which can misclassify
 * edge-case frames (e.g. dark loading screens vs. ban phase, or the "Selecting
 * hero" double-pick animation that has no coloured action button).
 * [PhaseOcrDetector] reads the visible text label at the top-centre of the
 * MLBB draft screen to produce a higher-confidence classification, and
 * additionally extracts semantic detail that the colour detector cannot see:
 *
 *  - Whether it is the **first or second ban round** ("First Ban Phase" vs
 *    "Second Ban Phase") — used to auto-advance the engine from BAN_ROUND_1
 *    to BAN_ROUND_2 without relying purely on slot-fill counts.
 *
 *  - Whether the current pick turn belongs to **ally or enemy** ("Ally Team
 *    Pick" vs "Enemy Team Pick" / "Your Turn To Pick") — used as a
 *    cross-validation guard against pick-sequence engine drift.
 *
 *  - **End-of-draft** detection via "The match is starting soon" / "Proceed
 *    to:" / "Battle Setup" — triggers [DraftSessionManager.completeDraft].
 *
 * Dependency: `com.google.mlkit:text-recognition:16.0.1` (declared in libs.versions.toml).
 *
 * The OCR scan is restricted to the top ~14 % of the frame where MLBB prints
 * phase labels, keeping per-frame latency low (~30 ms on mid-range hardware).
 * A second pass over a slightly wider region is **not** needed — all observed
 * phase labels fit within this band at every tested resolution.
 *
 * ### Observed MLBB phase label strings (source: screenshots 2026-06-21 / 2026-07-01)
 * | Screen state                           | OCR reads           |
 * |----------------------------------------|---------------------|
 * | First ban phase, our team acting       | "First Ban Phase"   |
 * | Second ban phase, our team acting      | "Second Ban Phase"  |
 * | Our team's pick turn                   | "Ally Team Pick"    |
 * | Our pick turn (your slot specifically) | "Your Turn To Pick" |
 * | Enemy's pick turn                      | "Enemy Team Pick"   |
 * | Double-pick animation (no grid shown)  | "Enemy Team Pick"   |
 * | "Selecting hero" interstitial          | "Player N Selecting Hero" |
 * | Match starting / skin selection        | "The match is starting soon" / "Proceed to:" |
 */
object PhaseOcrDetector {

    /**
     * Enriched OCR classification result.
     *
     * @param phase           Coarse phase classification.
     * @param confidence      0.0 – 1.0; prefer OCR over colour when ≥ [PhaseDetectionConfig.OCR_OVERRIDE_CONFIDENCE].
     * @param isBanRound2     True when "Second Ban Phase" was detected.  False for
     *                        "First Ban Phase" or any non-ban phase.  Used by
     *                        [OverlayCaptureCoordinator] to auto-advance the session.
     * @param isAllyPickTurn  True = "Ally Team Pick" / "Your Turn To Pick";
     *                        False = "Enemy Team Pick"; null = unknown (non-pick frames).
     * @param isPickAnimation True when the double-pick / "Selecting hero" animation is
     *                        on screen and no hero grid is visible.  Callers should
     *                        **skip** slot scanning for this frame to avoid false positives.
     */
    data class OcrResult(
        val phase:           DetectedPhase,
        val confidence:      Float,
        val isBanRound2:     Boolean  = false,
        val isAllyPickTurn:  Boolean? = null,
        val isPickAnimation: Boolean  = false
    )

    // Lazy singleton recognizer — ML Kit initialises its model on first use.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs ML Kit text recognition on [croppedBitmap] and returns an [OcrResult]
     * via [onResult] on the calling thread's Looper.
     *
     * **Callers are responsible for pre-cropping** to [SlotRegions.ocrTextRegion]
     * before calling this function.  The function no longer performs an internal
     * crop — the previous approach used a private `TEXT_REGION` that overlapped
     * with the caller's crop, producing a double-crop and silently discarding most
     * of the label text.
     *
     * [croppedBitmap] is recycled by the caller after [onResult] fires; do NOT
     * recycle it here.  Returns [OcrResult] with [DetectedPhase.UNKNOWN] and
     * confidence 0 when:
     *   - [InputImage.fromBitmap] fails (malformed bitmap)
     *   - ML Kit recognition fails or times out
     *   - No recognised keyword matches
     *
     * Callers should combine this result with [PhaseDetector.detect] results,
     * preferring OCR when confidence ≥ [PhaseDetectionConfig.OCR_OVERRIDE_CONFIDENCE].
     */
    fun detect(croppedBitmap: Bitmap, onResult: (OcrResult) -> Unit) {
        val image = runCatching { InputImage.fromBitmap(croppedBitmap, 0) }.getOrNull()
        if (image == null) {
            onResult(OcrResult(DetectedPhase.UNKNOWN, 0f))
            return
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.uppercase()
                onResult(classifyText(text))
            }
            .addOnFailureListener {
                onResult(OcrResult(DetectedPhase.UNKNOWN, 0f))
            }
    }

    /**
     * Maps raw OCR text to an [OcrResult].
     *
     * Priority order:
     * 1. End-of-draft signals ("STARTING", "PROCEED TO", "BATTLE SETUP") → LOADING.
     * 2. Ban phase ("BAN") — distinguishes first vs second round via "SECOND".
     * 3. Pick phase ("PICK", "YOUR TURN", "SELECTING") — distinguishes ally vs enemy,
     *    and flags the double-pick animation interstitial.
     * 4. Trading / Loading fallbacks.
     */
    internal fun classifyText(upperText: String): OcrResult {
        // ── End-of-draft / post-draft screens ───────────────────────────────
        // "The match is starting soon. Proceed to: Exp Lane" (screenshot 5, 20)
        // "Battle Setup" (screenshots 18-19)
        // These all mean the draft is over; trigger completeDraft().
        if ("STARTING" in upperText || "PROCEED TO" in upperText || "BATTLE SETUP" in upperText) {
            return OcrResult(DetectedPhase.LOADING, 0.92f)
        }

        // ── Trading phase ────────────────────────────────────────────────────
        if ("TRADING" in upperText) {
            return OcrResult(DetectedPhase.TRADING, 0.85f)
        }

        // ── Generic loading fallback ─────────────────────────────────────────
        if ("LOADING" in upperText) {
            return OcrResult(DetectedPhase.LOADING, 0.85f)
        }

        // ── Ban phase (First Ban Phase / Second Ban Phase) ───────────────────
        if ("BAN" in upperText) {
            val isRound2 = "SECOND" in upperText
            return OcrResult(
                phase       = DetectedPhase.BAN,
                confidence  = 0.90f,
                isBanRound2 = isRound2
            )
        }

        // ── Pick phase variants ───────────────────────────────────────────────
        // "Ally Team Pick" / "Your Turn To Pick"  → ally turn
        // "Enemy Team Pick"                       → enemy turn
        // "Player N Selecting Hero"               → double-pick animation interstitial
        if ("PICK" in upperText || "YOUR TURN" in upperText) {
            val isAlly = "ALLY" in upperText || "YOUR TURN" in upperText
            val isEnemy = "ENEMY" in upperText
            val isAllyPickTurn: Boolean? = when {
                isAlly  -> true
                isEnemy -> false
                else    -> null   // ambiguous (e.g. only "PICK" recognised)
            }
            return OcrResult(
                phase          = DetectedPhase.PICK,
                confidence     = 0.90f,
                isAllyPickTurn = isAllyPickTurn
            )
        }

        // "Player 1 Selecting Hero" / "Player 2 Selecting Hero" (double-pick animation,
        // screenshot 7) — still a PICK phase but NO hero grid is visible; callers
        // must skip slot scanning for this frame.
        if ("SELECTING" in upperText) {
            return OcrResult(
                phase          = DetectedPhase.PICK,
                confidence     = 0.80f,
                isAllyPickTurn = false,  // only enemy does double-pick in practice
                isPickAnimation = true
            )
        }

        return OcrResult(DetectedPhase.UNKNOWN, 0f)
    }
}
