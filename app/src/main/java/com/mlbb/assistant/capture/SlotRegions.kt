package com.mlbb.assistant.capture

import android.graphics.Rect
import com.mlbb.assistant.domain.engine.Rank

/**
 * Pixel region definitions for MLBB draft screen slots.
 *
 * All coordinates are expressed as fractions of screen width/height (0.0–1.0)
 * so they scale across device resolutions automatically.
 *
 * Reference frame: 1600 × 720 px landscape screenshot (MLBB draft pick screen).
 * Ban slot coordinates calibrated via Google OCR analysis on:
 *   - Screenshot_2026-06-19-18-34-58-655_com.mobile.legends.jpg (Legend rank)
 *   - Screenshot_2026-06-16-13-06-23-598_com.mobile.legends.jpg (Mythic rank)
 *
 * ┌──────────────────────────────────────────────────────────────────────────────────┐
 * │ [OB0][OB1][OB2][OB3][OB4]  ···  PHASE / TIMER  ···  [EB4][EB3][EB2][EB1][EB0] │
 * │  ←── ally bans, left-anchored, reveal inward ──       ── enemy bans, mirror ──→ │
 * ├───────────────┬──────────────────────────────────────┬─────────────────────────┤
 * │ OurPick0      │                                      │           EnemyPick0    │
 * │ OurPick1      │          CENTER HERO DISPLAY         │           EnemyPick1    │
 * │ OurPick2      │            (picking zone)            │           EnemyPick2    │
 * │ OurPick3      │                                      │           EnemyPick3    │
 * │ OurPick4      │                                      │           EnemyPick4    │
 * ├───────────────┴──────────────────────────────────────┴─────────────────────────┤
 * │                        [Show First]  [Help You Pick]                            │
 * └──────────────────────────────────────────────────────────────────────────────────┘
 *
 * BAN SLOT LAYOUT RULES (confirmed by OCR analysis):
 *   • Slots are anchored at the screen edges and reveal inward as rank increases.
 *   • The first 3 slots per team occupy the exact same screen positions regardless
 *     of whether there are 6, 8, or 10 total bans.
 *   • Slot 4 (index 3) appears at Legend rank (8 total bans).
 *   • Slot 5 (index 4) appears at Mythic rank and above (10 total bans).
 *   • Use [BanSlotTemplates] to get only the slots active for a given rank tier.
 *
 * IMPORTANT — Orientation:
 *   MLBB draft is ALWAYS landscape. Do NOT use portrait-mode coordinates.
 */
data class SlotRegionF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toRect(screenW: Int, screenH: Int) = Rect(
        (left   * screenW).toInt(),
        (top    * screenH).toInt(),
        (right  * screenW).toInt(),
        (bottom * screenH).toInt()
    )
}

// ─────────────────────────────────────────────────────────────────────────────────
// BAN DRAFT TYPE — rank-to-slot-count mapping
// ─────────────────────────────────────────────────────────────────────────────────

/**
 * The three distinct ban configurations used in MLBB ranked drafts.
 *
 * Maps directly to [Rank] via [fromRank]. Used by [BanSlotTemplates] and
 * [FrameProcessor] to restrict slot scanning to only the slots that are
 * visible for the current rank tier — avoiding false-positive detections on
 * empty slot positions that are not rendered on screen.
 */
enum class BanDraftType(
    val totalBans: Int,
    val slotsPerTeam: Int,
    val hasRound2: Boolean
) {
    /** Epic and below: 3 bans per team, no round 2. Slots 0–2 active. */
    EPIC_6_BANS(totalBans = 6, slotsPerTeam = 3, hasRound2 = false),

    /** Legend: 4 bans per team, round 2 adds 1 ban each. Slots 0–3 active. */
    LEGEND_8_BANS(totalBans = 8, slotsPerTeam = 4, hasRound2 = true),

    /** Mythic and above: 5 bans per team, round 2 adds 2 bans each. Slots 0–4 active. */
    MYTHIC_10_BANS(totalBans = 10, slotsPerTeam = 5, hasRound2 = true);

    companion object {
        /**
         * Returns the [BanDraftType] for a given [Rank].
         * Warrior/Elite/Master/Epic and UNKNOWN all use the 6-ban structure.
         */
        fun fromRank(rank: Rank): BanDraftType = when (rank) {
            Rank.LEGEND                                                      -> LEGEND_8_BANS
            Rank.MYTHIC, Rank.MYTHICAL_HONOR, Rank.MYTHICAL_GLORY,
            Rank.IMMORTAL                                                    -> MYTHIC_10_BANS
            else                                                             -> EPIC_6_BANS
        }

        /** Infers [BanDraftType] from an observed total ban count (fallback path). */
        fun fromObservedBanCount(observed: Int): BanDraftType = when {
            observed >= 10 -> MYTHIC_10_BANS
            observed >= 8  -> LEGEND_8_BANS
            else           -> EPIC_6_BANS
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────
// BAN SLOT TEMPLATE — predefined gridded layout per rank tier
// ─────────────────────────────────────────────────────────────────────────────────

/**
 * A rank-specific ban scan template.
 *
 * Contains only the slot regions that are **rendered on screen** for the given
 * [draftType]. The CV pipeline should use [BanSlotTemplates.forDraftType] to
 * retrieve the correct template before scanning, instead of always iterating
 * all five slots.
 *
 * @param draftType      The rank tier this template applies to.
 * @param ourBanSlots    Active ally ban slot regions, index 0 = leftmost.
 * @param enemyBanSlots  Active enemy ban slot regions, index 0 = rightmost.
 */
data class BanSlotTemplate(
    val draftType: BanDraftType,
    val ourBanSlots: List<SlotRegionF>,
    val enemyBanSlots: List<SlotRegionF>
) {
    init {
        require(ourBanSlots.size   == draftType.slotsPerTeam) {
            "ourBanSlots size ${ourBanSlots.size} must equal slotsPerTeam ${draftType.slotsPerTeam}"
        }
        require(enemyBanSlots.size == draftType.slotsPerTeam) {
            "enemyBanSlots size ${enemyBanSlots.size} must equal slotsPerTeam ${draftType.slotsPerTeam}"
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────
// SLOT REGIONS — normalised coordinates (0.0–1.0)
// ─────────────────────────────────────────────────────────────────────────────────

object SlotRegions {

    // ── Reference resolution ──────────────────────────────────────────────────
    // Used only for documentation; all values below are normalized (0.0–1.0).
    const val REF_WIDTH  = 1600
    const val REF_HEIGHT = 720

    // ─────────────────────────────────────────────────────────────────────────
    // BAN SLOTS — top bar, y ≈ 1.5–8.5 % of screen height
    //
    // Calibration source: Google OCR analysis on Legend + Mythic screenshots.
    // Key insight: slots are edge-anchored; higher ranks reveal additional
    // slots inward toward the center timer. The first 3 per team are fixed.
    //
    // Ally bans:   LEFT side, ordered 0 (leftmost) → 4 (nearest center)
    // Enemy bans: RIGHT side, ordered 0 (rightmost) → 4 (nearest center)
    //
    // All 5 slots are defined here. Use BanSlotTemplates to get the subset
    // active for the current rank (3, 4, or 5 slots per team).
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * All 5 ally ban slot regions — top-left corner, left-to-right.
     * Slots 0–2 are always visible; slot 3 appears at Legend+; slot 4 at Mythic+.
     *
     * Abs reference (1600 × 720): slot N center_x ≈ 115 + N*43 px, center_y ≈ 36 px
     */
    val ourBanSlots = listOf(
        SlotRegionF(0.0555f, 0.015f, 0.0885f, 0.085f),  // slot 0  cx≈0.072  active: all ranks
        SlotRegionF(0.0985f, 0.015f, 0.1315f, 0.085f),  // slot 1  cx≈0.115  active: all ranks
        SlotRegionF(0.1415f, 0.015f, 0.1745f, 0.085f),  // slot 2  cx≈0.158  active: all ranks
        SlotRegionF(0.1845f, 0.015f, 0.2175f, 0.085f),  // slot 3  cx≈0.201  active: Legend+
        SlotRegionF(0.2275f, 0.015f, 0.2605f, 0.085f)   // slot 4  cx≈0.244  active: Mythic+
    )

    /**
     * All 5 enemy ban slot regions — top-right corner, right-to-left.
     * Mirrors ally layout exactly; slot 0 is outermost (far right).
     *
     * Abs reference (1600 × 720): slot N center_x ≈ 1485 - N*43 px, center_y ≈ 36 px
     */
    val enemyBanSlots = listOf(
        SlotRegionF(0.9115f, 0.015f, 0.9445f, 0.085f),  // slot 0  cx≈0.928  active: all ranks
        SlotRegionF(0.8685f, 0.015f, 0.9015f, 0.085f),  // slot 1  cx≈0.885  active: all ranks
        SlotRegionF(0.8255f, 0.015f, 0.8585f, 0.085f),  // slot 2  cx≈0.842  active: all ranks
        SlotRegionF(0.7825f, 0.015f, 0.8155f, 0.085f),  // slot 3  cx≈0.799  active: Legend+
        SlotRegionF(0.7395f, 0.015f, 0.7725f, 0.085f)   // slot 4  cx≈0.756  active: Mythic+
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PICK SLOTS — side panels, 5 stacked rows each side
    //
    // Row pitch: ~93 px.  First row top: ~65 px, last row bottom: ~530 px.
    //   Row N: top = 65 + N*93,  bottom = 158 + N*93
    //
    // Our picks:    LEFT panel,  hero portrait x ≈  73-180  (0.046–0.113)
    // Enemy picks: RIGHT panel,  hero portrait x ≈ 1420-1527 (0.888–0.955)
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
    // ─────────────────────────────────────────────────────────────────────────

    /** Phase label — "Enemy Team Pick", "Our Team Pick", "Ban Phase", etc. */
    val phaseBanner = SlotRegionF(0.344f, 0.007f, 0.656f, 0.050f)

    /** Countdown timer digit(s) below the phase label. */
    val countdownTimer = SlotRegionF(0.438f, 0.044f, 0.563f, 0.100f)

    // ─────────────────────────────────────────────────────────────────────────
    // MISCELLANEOUS
    // ─────────────────────────────────────────────────────────────────────────

    /** Center hero display — the large hero portrait rendered during hover/lock-in. */
    val centerHeroDisplay = SlotRegionF(0.194f, 0.083f, 0.806f, 0.889f)

    /**
     * Action button area — contains "Show First" / "Help You Pick" during pick phase,
     * or the red BAN button during ban phase.
     */
    val actionButton = SlotRegionF(0.150f, 0.861f, 0.425f, 0.944f)

    /** Rank emblem — top-left of the left panel (our team side). */
    val rankEmblem = SlotRegionF(0.003f, 0.090f, 0.038f, 0.160f)

    /** First-pick indicator — near the phase banner when our team picks first. */
    val firstPickIndicator = SlotRegionF(0.344f, 0.069f, 0.469f, 0.111f)

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    fun cropSlot(frame: android.graphics.Bitmap, region: SlotRegionF): android.graphics.Bitmap {
        val r     = region.toRect(frame.width, frame.height)
        val safeW = (r.right  - r.left).coerceAtLeast(1).coerceAtMost(frame.width  - r.left)
        val safeH = (r.bottom - r.top ).coerceAtLeast(1).coerceAtMost(frame.height - r.top)
        return android.graphics.Bitmap.createBitmap(frame, r.left, r.top, safeW, safeH)
    }
}

// ─────────────────────────────────────────────────────────────────────────────────
// BAN SLOT TEMPLATES — predefined scan grids per rank tier
// ─────────────────────────────────────────────────────────────────────────────────

/**
 * Predefined ban scan templates for each rank tier.
 *
 * The CV pipeline calls [forDraftType] (or [forRank]) to get the correct
 * template before scanning ban slots, ensuring only the slots actually
 * rendered on screen are checked. Scanning a slot that isn't visible for
 * the current rank produces false positives from adjacent UI elements.
 *
 * Usage:
 * ```kotlin
 * val template = BanSlotTemplates.forRank(currentRank)
 * template.ourBanSlots.forEachIndexed { i, region -> ... }
 * ```
 */
object BanSlotTemplates {

    /**
     * Epic and below — 3 bans per team (6 total).
     * Slots 0–2 only (outermost three on each side).
     */
    val epic6Bans = BanSlotTemplate(
        draftType      = BanDraftType.EPIC_6_BANS,
        ourBanSlots    = SlotRegions.ourBanSlots.subList(0, 3),
        enemyBanSlots  = SlotRegions.enemyBanSlots.subList(0, 3)
    )

    /**
     * Legend — 4 bans per team (8 total).
     * Slots 0–3; slot 3 is the round-2 ban, revealed inward from the edge.
     */
    val legend8Bans = BanSlotTemplate(
        draftType      = BanDraftType.LEGEND_8_BANS,
        ourBanSlots    = SlotRegions.ourBanSlots.subList(0, 4),
        enemyBanSlots  = SlotRegions.enemyBanSlots.subList(0, 4)
    )

    /**
     * Mythic and above — 5 bans per team (10 total).
     * All slots 0–4 active. Slot 4 is the second round-2 ban, innermost position.
     */
    val mythic10Bans = BanSlotTemplate(
        draftType      = BanDraftType.MYTHIC_10_BANS,
        ourBanSlots    = SlotRegions.ourBanSlots,
        enemyBanSlots  = SlotRegions.enemyBanSlots
    )

    /** Returns the prebuilt [BanSlotTemplate] for the given [BanDraftType]. */
    fun forDraftType(type: BanDraftType): BanSlotTemplate = when (type) {
        BanDraftType.EPIC_6_BANS    -> epic6Bans
        BanDraftType.LEGEND_8_BANS  -> legend8Bans
        BanDraftType.MYTHIC_10_BANS -> mythic10Bans
    }

    /** Convenience overload — resolves [Rank] to [BanDraftType] then returns the template. */
    fun forRank(rank: Rank): BanSlotTemplate = forDraftType(BanDraftType.fromRank(rank))
}
