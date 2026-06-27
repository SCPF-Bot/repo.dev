package com.mlbb.assistant.presentation.settings.components

import org.json.JSONArray
import org.json.JSONObject

// ── Enums & data types ────────────────────────────────────────────────────────

enum class SlotTeam { ALLY, ENEMY }

/**
 * Normalised (0..1) coordinate of a mapped portrait point with team assignment.
 *
 * Coordinates are relative to the *rendered image bounds* (not the container),
 * so they remain stable even when the image is letterboxed inside a larger canvas.
 *
 * The [team] field defaults to [SlotTeam.ALLY] for backward compatibility with
 * JSON written by older app versions that did not include a "team" key.
 */
internal data class MappedPoint(
    val x:    Float,
    val y:    Float,
    val team: SlotTeam = SlotTeam.ALLY
)

// ── JSON serialisation ────────────────────────────────────────────────────────

internal fun parseMappedPoints(json: String): List<MappedPoint> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj  = arr.getJSONObject(i)
            val team = runCatching { SlotTeam.valueOf(obj.optString("team", "ALLY")) }
                .getOrDefault(SlotTeam.ALLY)
            MappedPoint(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat(), team)
        }
    }.getOrDefault(emptyList())
}

internal fun serializeMappedPoints(points: List<MappedPoint>): String {
    val arr = JSONArray()
    points.forEach { p ->
        arr.put(
            JSONObject()
                .put("x",    p.x.toDouble())
                .put("y",    p.y.toDouble())
                .put("team", p.team.name)
        )
    }
    return arr.toString()
}

// ── Slot label helpers ────────────────────────────────────────────────────────

internal fun List<MappedPoint>.slotLabel(point: MappedPoint): String {
    return if (point.team == SlotTeam.ALLY) {
        val idx = filter { it.team == SlotTeam.ALLY }.indexOf(point) + 1
        "A$idx"
    } else {
        val idx = filter { it.team == SlotTeam.ENEMY }.indexOf(point) + 1
        "E$idx"
    }
}

// ── Hit-test radius ───────────────────────────────────────────────────────────

internal const val HIT_RADIUS_PX = 44f

// ── Pre-built layout templates ────────────────────────────────────────────────

/**
 * 6-ban layout (3 ally + 3 enemy) — standard for Epic/Legend rank.
 *
 * Coordinates are normalised fractions of the *rendered image* (not the full screen).
 * In a typical MLBB landscape ban-phase screenshot the ban icons appear in a single
 * row near the top of the game area:
 *   - Ally bans cluster in the left ~30 % of the image, y ≈ 13–16 % from the top.
 *   - Enemy bans cluster in the right ~30 %, mirrored.
 *
 * Users should drag-to-fine-tune after applying a template.
 */
internal val TEMPLATE_3_PLUS_3 = listOf(
    // Ally — left side, single row
    MappedPoint(0.095f, 0.145f, SlotTeam.ALLY),
    MappedPoint(0.180f, 0.145f, SlotTeam.ALLY),
    MappedPoint(0.265f, 0.145f, SlotTeam.ALLY),
    // Enemy — right side, single row (mirrored)
    MappedPoint(0.735f, 0.145f, SlotTeam.ENEMY),
    MappedPoint(0.820f, 0.145f, SlotTeam.ENEMY),
    MappedPoint(0.905f, 0.145f, SlotTeam.ENEMY),
)

/**
 * 10-ban layout (5 ally + 5 enemy) — two ban rounds, used in Mythic+ rank.
 *
 * Round 1 bans (3+3) appear in the first row at y ≈ 13 %.
 * Round 2 bans (2+2) appear in the second row at y ≈ 27 %.
 */
internal val TEMPLATE_5_PLUS_5 = listOf(
    // Ally row 1
    MappedPoint(0.068f, 0.130f, SlotTeam.ALLY),
    MappedPoint(0.153f, 0.130f, SlotTeam.ALLY),
    MappedPoint(0.238f, 0.130f, SlotTeam.ALLY),
    // Ally row 2
    MappedPoint(0.088f, 0.268f, SlotTeam.ALLY),
    MappedPoint(0.173f, 0.268f, SlotTeam.ALLY),
    // Enemy row 1
    MappedPoint(0.762f, 0.130f, SlotTeam.ENEMY),
    MappedPoint(0.847f, 0.130f, SlotTeam.ENEMY),
    MappedPoint(0.932f, 0.130f, SlotTeam.ENEMY),
    // Enemy row 2
    MappedPoint(0.827f, 0.268f, SlotTeam.ENEMY),
    MappedPoint(0.912f, 0.268f, SlotTeam.ENEMY),
)
