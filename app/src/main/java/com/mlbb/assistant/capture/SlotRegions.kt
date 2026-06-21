package com.mlbb.assistant.capture

import android.graphics.Rect

/**
 * Pixel region definitions for MLBB draft screen slots.
 *
 * All coordinates are expressed as fractions of screen width/height (0.0–1.0)
 * so they scale across device resolutions automatically.
 *
 * Reference frame: 1600 × 720 px landscape screenshot (MLBB draft pick screen).
 * Calibrated from: Screenshot_2026-06-19-18-34-58-655_com.mobile.legends.jpg
 *
 * ┌────────────────────────────────────────────────────────────────────────────┐
 * │ [OurBan0][OurBan1][OurBan2][OurBan3][OurBan4]  PHASE / TIMER  [EB4]…[EB0]│
 * ├───────────────┬──────────────────────────────────┬───────────────────────┤
 * │ OurPick0      │                                  │         EnemyPick0    │
 * │ OurPick1      │        CENTER HERO DISPLAY       │         EnemyPick1    │
 * │ OurPick2      │          (picking zone)          │         EnemyPick2    │
 * │ OurPick3      │                                  │         EnemyPick3    │
 * │ OurPick4      │                                  │         EnemyPick4    │
 * ├───────────────┴──────────────────────────────────┴───────────────────────┤
 * │             [Show First]  [Help You Pick]                                 │
 * └────────────────────────────────────────────────────────────────────────────┘
 *
 * IMPORTANT — Orientation:
 *   MLBB draft is ALWAYS landscape. The previous portrait-based coordinates
 *   (banSlots spanning full width top 4–24 %) were incorrect for the actual game UI.
 *   All values below were re-calibrated from the 1600 × 720 reference image.
 */
data class SlotRegionF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toRect(screenW: Int, screenH: Int) = Rect(
        (left   * screenW).toInt(),
        (top    * screenH).toInt(),
        (right  * screenW).toInt(),
        (bottom * screenH).toInt()
    )
}

object SlotRegions {

    // ── Reference resolution ──────────────────────────────────────────────────
    // Used only for documentation; all values below are normalized (0.0–1.0).
    const val REF_WIDTH  = 1600
    const val REF_HEIGHT = 720

    // ─────────────────────────────────────────────────────────────────────────
    // BAN SLOTS — top bar, ~4–56 px tall (normalized 0.006–0.078 of 720 px)
    //
    // Our bans:    LEFT side,  slots 0→4 ordered left-to-right
    // Enemy bans:  RIGHT side, slots 0→4 ordered right-to-left (slot 0 = far right)
    //
    // Reference absolute px per slot (52 × 52 circle, 56 px pitch):
    //   Our slot N:    left = 5 + N*56,  right = 57 + N*56
    //   Enemy slot N:  right = 1595 - N*56, left = 1543 - N*56
    // ─────────────────────────────────────────────────────────────────────────

    /** Our team's 5 ban slots — top-left corner, ordered 0 (leftmost) → 4. */
    val ourBanSlots = listOf(
        SlotRegionF(0.003f, 0.006f, 0.036f, 0.078f),   // slot 0  abs: x  5-57
        SlotRegionF(0.038f, 0.006f, 0.071f, 0.078f),   // slot 1  abs: x 61-113
        SlotRegionF(0.074f, 0.006f, 0.107f, 0.078f),   // slot 2  abs: x 117-169
        SlotRegionF(0.109f, 0.006f, 0.142f, 0.078f),   // slot 3  abs: x 173-225
        SlotRegionF(0.144f, 0.006f, 0.177f, 0.078f)    // slot 4  abs: x 229-281
    )

    /** Enemy team's 5 ban slots — top-right corner, ordered 0 (rightmost) → 4. */
    val enemyBanSlots = listOf(
        SlotRegionF(0.964f, 0.006f, 0.997f, 0.078f),   // slot 0  abs: x 1543-1595
        SlotRegionF(0.929f, 0.006f, 0.962f, 0.078f),   // slot 1  abs: x 1487-1539
        SlotRegionF(0.894f, 0.006f, 0.927f, 0.078f),   // slot 2  abs: x 1431-1483
        SlotRegionF(0.859f, 0.006f, 0.892f, 0.078f),   // slot 3  abs: x 1375-1427
        SlotRegionF(0.824f, 0.006f, 0.857f, 0.078f)    // slot 4  abs: x 1319-1371
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PICK SLOTS — side panels, 5 stacked rows each side
    //
    // Row pitch: ~93 px.  First row top: ~65 px, last row bottom: ~558 px.
    //   Row N: top = 65 + N*93,  bottom = 158 + N*93
    //
    // Our picks:    LEFT panel,  hero portrait region x ≈  73-180  (0.046–0.113)
    // Enemy picks:  RIGHT panel, hero portrait region x ≈ 1420-1527 (0.888–0.955)
    // ─────────────────────────────────────────────────────────────────────────

    /** Our team's 5 pick slots — left panel, ordered 0 (top) → 4 (bottom). */
    val ourPickSlots = listOf(
        SlotRegionF(0.046f, 0.090f, 0.113f, 0.219f),   // row 0  abs: y  65-158
        SlotRegionF(0.046f, 0.219f, 0.113f, 0.349f),   // row 1  abs: y 158-251
        SlotRegionF(0.046f, 0.349f, 0.113f, 0.478f),   // row 2  abs: y 251-344
        SlotRegionF(0.046f, 0.478f, 0.113f, 0.607f),   // row 3  abs: y 344-437
        SlotRegionF(0.046f, 0.607f, 0.113f, 0.736f)    // row 4  abs: y 437-530
    )

    /** Enemy team's 5 pick slots — right panel, ordered 0 (top) → 4 (bottom). */
    val enemyPickSlots = listOf(
        SlotRegionF(0.888f, 0.090f, 0.955f, 0.219f),   // row 0  abs: y  65-158
        SlotRegionF(0.888f, 0.219f, 0.955f, 0.349f),   // row 1  abs: y 158-251
        SlotRegionF(0.888f, 0.349f, 0.955f, 0.478f),   // row 2  abs: y 251-344
        SlotRegionF(0.888f, 0.478f, 0.955f, 0.607f),   // row 3  abs: y 344-437
        SlotRegionF(0.888f, 0.607f, 0.955f, 0.736f)    // row 4  abs: y 437-530
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE BANNER + COUNTDOWN TIMER — top-center
    //
    // The banner shows "Enemy Team Pick" / "Our Team Pick" / "Ban Phase" etc.
    // The countdown number sits just below the banner text.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Phase label region — contains text such as "Enemy Team Pick" or "Ban Phase".
     * Abs: x 550–1050, y 5–32  (normalized: 0.344–0.656 × 0.007–0.044)
     */
    val phaseBanner = SlotRegionF(0.344f, 0.007f, 0.656f, 0.050f)

    /**
     * Countdown timer digit(s) — the large number shown below the phase label.
     * Abs: x 700–900, y 32–68  (normalized: 0.438–0.563 × 0.044–0.094)
     */
    val countdownTimer = SlotRegionF(0.438f, 0.044f, 0.563f, 0.100f)

    // ─────────────────────────────────────────────────────────────────────────
    // MISCELLANEOUS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Center hero display — the large hero portrait rendered while a player
     * is hovering or locking a hero. Useful for identifying "currently being
     * considered" hero before lock-in.
     * Abs: x 310–1290, y 60–640
     */
    val centerHeroDisplay = SlotRegionF(0.194f, 0.083f, 0.806f, 0.889f)

    /**
     * Action button area — bottom-center, contains "Show First" / "Help You Pick"
     * during pick phase, or the red BAN button during ban phase.
     * Abs: x 240–680, y 620–680
     */
    val actionButton = SlotRegionF(0.150f, 0.861f, 0.425f, 0.944f)

    /**
     * Rank emblem — top-left of the left panel (our team side).
     * Abs: x 5–60, y 65–115
     */
    val rankEmblem = SlotRegionF(0.003f, 0.090f, 0.038f, 0.160f)

    /**
     * First-pick indicator — appears near the phase banner when our team has
     * first pick. Abs: x 550–750, y 50–80
     */
    val firstPickIndicator = SlotRegionF(0.344f, 0.069f, 0.469f, 0.111f)

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    fun cropSlot(frame: android.graphics.Bitmap, region: SlotRegionF): android.graphics.Bitmap {
        val r    = region.toRect(frame.width, frame.height)
        val safeW = (r.right  - r.left).coerceAtLeast(1).coerceAtMost(frame.width  - r.left)
        val safeH = (r.bottom - r.top ).coerceAtLeast(1).coerceAtMost(frame.height - r.top)
        return android.graphics.Bitmap.createBitmap(frame, r.left, r.top, safeW, safeH)
    }
}
