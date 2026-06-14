package com.mlbb.assistant.capture

import android.graphics.Rect

/**
 * Pixel region definitions for MLBB draft screen slots.
 *
 * All coordinates are expressed as fractions of screen width/height
 * so they scale across device resolutions.
 *
 * Layout (portrait orientation):
 *   Enemy ban row:  top 8–14% of screen, split into 5 slots
 *   Your ban row:   top 15–21%
 *   Enemy picks:    left side, 25–75% height, 5 stacked
 *   Your picks:     right side, 25–75% height, 5 stacked
 */
data class SlotRegionF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toRect(screenW: Int, screenH: Int) = Rect(
        (left  * screenW).toInt(),
        (top   * screenH).toInt(),
        (right * screenW).toInt(),
        (bottom* screenH).toInt()
    )
}

object SlotRegions {

    // ── Enemy ban slots (top row, 3–5 slots depending on rank) ──────────────
    val enemyBanSlots = listOf(
        SlotRegionF(0.10f, 0.04f, 0.25f, 0.14f),
        SlotRegionF(0.27f, 0.04f, 0.42f, 0.14f),
        SlotRegionF(0.44f, 0.04f, 0.59f, 0.14f),
        SlotRegionF(0.61f, 0.04f, 0.76f, 0.14f),
        SlotRegionF(0.78f, 0.04f, 0.93f, 0.14f)
    )

    // ── Our ban slots (second row) ───────────────────────────────────────────
    val ourBanSlots = listOf(
        SlotRegionF(0.10f, 0.15f, 0.25f, 0.24f),
        SlotRegionF(0.27f, 0.15f, 0.42f, 0.24f),
        SlotRegionF(0.44f, 0.15f, 0.59f, 0.24f),
        SlotRegionF(0.61f, 0.15f, 0.76f, 0.24f),
        SlotRegionF(0.78f, 0.15f, 0.93f, 0.24f)
    )

    // ── Enemy pick slots (left panel, 5 stacked) ─────────────────────────────
    val enemyPickSlots = listOf(
        SlotRegionF(0.02f, 0.25f, 0.20f, 0.35f),
        SlotRegionF(0.02f, 0.37f, 0.20f, 0.47f),
        SlotRegionF(0.02f, 0.49f, 0.20f, 0.59f),
        SlotRegionF(0.02f, 0.61f, 0.20f, 0.71f),
        SlotRegionF(0.02f, 0.73f, 0.20f, 0.83f)
    )

    // ── Our pick slots (right panel, 5 stacked) ──────────────────────────────
    val ourPickSlots = listOf(
        SlotRegionF(0.80f, 0.25f, 0.98f, 0.35f),
        SlotRegionF(0.80f, 0.37f, 0.98f, 0.47f),
        SlotRegionF(0.80f, 0.49f, 0.98f, 0.59f),
        SlotRegionF(0.80f, 0.61f, 0.98f, 0.71f),
        SlotRegionF(0.80f, 0.73f, 0.98f, 0.83f)
    )

    // ── Rank emblem region (top-left corner) ─────────────────────────────────
    val rankEmblem = SlotRegionF(0.02f, 0.02f, 0.18f, 0.10f)

    // ── First-pick indicator (top-centre) ────────────────────────────────────
    val firstPickIndicator = SlotRegionF(0.40f, 0.01f, 0.60f, 0.06f)

    // ── Red Ban button / Blue Pick button (bottom-centre) ────────────────────
    val actionButton = SlotRegionF(0.33f, 0.80f, 0.67f, 0.92f)

    fun cropSlot(frame: android.graphics.Bitmap, region: SlotRegionF): android.graphics.Bitmap {
        val r = region.toRect(frame.width, frame.height)
        val safeW = (r.right - r.left).coerceAtLeast(1).coerceAtMost(frame.width  - r.left)
        val safeH = (r.bottom - r.top).coerceAtLeast(1).coerceAtMost(frame.height - r.top)
        return android.graphics.Bitmap.createBitmap(frame, r.left, r.top, safeW, safeH)
    }
}
