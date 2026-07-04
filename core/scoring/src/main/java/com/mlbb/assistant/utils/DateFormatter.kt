package com.mlbb.assistant.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure utility object for formatting Unix millisecond timestamps into
 * human-readable strings.
 *
 * Uses [DateTimeFormatter] (API 26+, minSdk = 29 ✅) which is thread-safe
 * by design — formatters are created once and reused across all calls.
 */
object DateFormatter {

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR   = 3_600_000L
    private const val MILLIS_PER_DAY    = 86_400_000L

    private val ABSOLUTE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    private val FULL_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault())

    private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /**
     * ISO-style log formatter: "yyyy-MM-dd HH:mm:ss".
     * Locale-independent — always uses digits, no localised month names.
     * Used for CSV exports where machine-parseable output is required.
     */
    private val LOG_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

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
        val deltaMs = System.currentTimeMillis() - timestampMs

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
                val time = timestampMs.toLocalDateTime().format(TIME_FORMATTER)
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
        timestampMs.toLocalDateTime().format(ABSOLUTE_FORMATTER)

    /**
     * Formats [timestampMs] as a full date-time string (e.g. "Jun 15, 2026 18:30").
     * Use for detail screens or export labels.
     */
    fun formatFull(timestampMs: Long): String =
        timestampMs.toLocalDateTime().format(FULL_FORMATTER)

    /**
     * Formats [timestampMs] as an ISO-style log string (e.g. "2026-06-15 18:30:45").
     * Locale-independent — safe for machine-parseable log files and CSV exports.
     * Use for CSV exports.
     */
    fun formatLog(timestampMs: Long): String =
        timestampMs.toLocalDateTime().format(LOG_FORMATTER)
}
