package com.mlbb.assistant.presentation.settings.components

import org.json.JSONArray
import org.json.JSONObject

// ── Enums & data types ────────────────────────────────────────────────────────

enum class SlotTeam { ALLY, ENEMY }

/**
 * Normalised (0..1) coordinate of a mapped portrait point with team assignment.
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
 * Positions are approximate normalised coords for a typical MLBB landscape ban screen.
 * Users can drag-to-adjust after applying.
 */
internal val TEMPLATE_3_PLUS_3 = listOf(
    MappedPoint(0.083f, 0.115f, SlotTeam.ALLY),
    MappedPoint(0.170f, 0.115f, SlotTeam.ALLY),
    MappedPoint(0.257f, 0.115f, SlotTeam.ALLY),
    MappedPoint(0.743f, 0.115f, SlotTeam.ENEMY),
    MappedPoint(0.830f, 0.115f, SlotTeam.ENEMY),
    MappedPoint(0.917f, 0.115f, SlotTeam.ENEMY),
)

/**
 * 10-ban layout (5 ally + 5 enemy) — two rounds, used in higher ranks.
 * Round-1 bans appear at y≈0.115; round-2 bans appear at y≈0.240.
 */
internal val TEMPLATE_5_PLUS_5 = listOf(
    MappedPoint(0.057f, 0.115f, SlotTeam.ALLY),
    MappedPoint(0.140f, 0.115f, SlotTeam.ALLY),
    MappedPoint(0.223f, 0.115f, SlotTeam.ALLY),
    MappedPoint(0.080f, 0.240f, SlotTeam.ALLY),
    MappedPoint(0.163f, 0.240f, SlotTeam.ALLY),
    MappedPoint(0.777f, 0.115f, SlotTeam.ENEMY),
    MappedPoint(0.860f, 0.115f, SlotTeam.ENEMY),
    MappedPoint(0.943f, 0.115f, SlotTeam.ENEMY),
    MappedPoint(0.837f, 0.240f, SlotTeam.ENEMY),
    MappedPoint(0.920f, 0.240f, SlotTeam.ENEMY),
)
