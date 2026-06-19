package com.mlbb.assistant.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure utility object for formatting Unix millisecond timestamps into
 * human-readable strings.
 *
 * All formatting is locale-aware using [Locale.getDefault()] so the output
 * adapts to the user's device locale without additional configuration.
 *
 * Thread safety: [SimpleDateFormat] is NOT thread-safe. Every call creates a
 * new formatter instance — acceptable because this is a UI utility used only
 * on the main thread. If performance becomes a concern, use [java.time.format.DateTimeFormatter]
 * (API 26+) which is thread-safe by design.
 */
object DateFormatter {

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR   = 3_600_000L
    private const val MILLIS_PER_DAY    = 86_400_000L

    /**
     * Formats [timestampMs] as a short relative time string when the event is
     * recent (< 24 hours ago), or as an absolute date otherwise.
     *
     * Examples:
     *   - 30 seconds ago → "Just now"
     *   - 45 minutes ago → "45m ago"
     *   - 3 hours ago    → "3h ago"
     *   - Yesterday      → "Yesterday, 18:30"
     *   - Older          → "Jun 15, 2026"
     */
    fun formatRelative(timestampMs: Long): String {
        val nowMs  = System.currentTimeMillis()
        val deltaMs = nowMs - timestampMs

        return when {
            deltaMs < MILLIS_PER_MINUTE ->
                "Just now"
            deltaMs < MILLIS_PER_HOUR -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
                "${minutes}m ago"
            }
            deltaMs < MILLIS_PER_DAY -> {
                val hours = TimeUnit.MILLISECONDS.toHours(deltaMs)
                "${hours}h ago"
            }
            deltaMs < MILLIS_PER_DAY * 2 -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
                "Yesterday, $time"
            }
            else -> formatAbsolute(timestampMs)
        }
    }

    /**
     * Formats [timestampMs] as "MMM d, yyyy" (e.g. "Jun 15, 2026").
     * Use for history list items where relative time is not meaningful.
     */
    fun formatAbsolute(timestampMs: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))

    /**
     * Formats [timestampMs] as a full date-time string (e.g. "Jun 15, 2026 18:30").
     * Use for detail screens or export labels.
     */
    fun formatFull(timestampMs: Long): String =
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(timestampMs))
}
