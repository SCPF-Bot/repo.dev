package com.mlbb.assistant.data.local.crashlog

import android.content.Context
import com.mlbb.assistant.utils.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class LogLevel(val label: String) {
    CRASH("CRASH"),
    ERROR("ERROR"),
    WARN("WARN"),
    INFO("INFO"),
    DEBUG("DEBUG")
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String = ""
) {
    val formattedTime: String
        get() = DateFormatter.formatLog(timestamp)
}

/**
 * Simple append-only log store backed by a plain text file in the app's
 * private files directory.  Each entry is written as a fixed-format line:
 *
 *   TIMESTAMP|LEVEL|TAG|MESSAGE
 *
 * Stack-traces (multi-line) are written as additional lines each prefixed
 * with TAB ("\\t") and are collected back onto the preceding entry when
 * reading.
 *
 * The file is capped at [MAX_BYTES] to prevent unbounded growth.
 *
 * TD-11: [appendSync] now acquires [lock] before writing so concurrent
 * crash-handler invocations on different threads cannot interleave bytes.
 */
object CrashLogStore {

    private const val LOG_FILE_NAME = "mlbb_crash_log.txt"
    private const val MAX_BYTES     = 512 * 1024L  // 512 KB
    private const val MAX_ENTRIES   = 500

    /**
     * TD-11: Mutex for [appendSync].  A plain Java object is used so the
     * lock is available in non-suspend contexts (crash handlers, JNI callbacks).
     */
    private val lock = Any()

    private fun logFile(context: Context): File =
        File(context.filesDir, LOG_FILE_NAME)

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun append(context: Context, entry: LogEntry) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            runCatching {
                val file = logFile(context)
                if (file.exists() && file.length() > MAX_BYTES) rotate(file)
                file.appendText(encode(entry))
            }
        }
    }

    /**
     * TD-11: Acquires [lock] before every file operation so concurrent
     * crash-handler calls on separate threads cannot produce interleaved output.
     */
    fun appendSync(context: Context, entry: LogEntry) {
        synchronized(lock) {
            runCatching {
                val file = logFile(context)
                if (file.exists() && file.length() > MAX_BYTES) rotate(file)
                file.appendText(encode(entry))
            }
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun readAll(context: Context): List<LogEntry> = withContext(Dispatchers.IO) {
        runCatching { decode(logFile(context).readLines()) }.getOrDefault(emptyList())
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            runCatching { logFile(context).delete() }
        }
    }

    // ── Encoding / decoding ───────────────────────────────────────────────────

    private fun encode(entry: LogEntry): String {
        val sb = StringBuilder()
        val escapedMsg = entry.message.replace("|", "¦").replace("\n", "↵")
        sb.appendLine("${entry.timestamp}|${entry.level.name}|${entry.tag}|$escapedMsg")
        if (entry.stackTrace.isNotBlank()) {
            entry.stackTrace.lines().forEach { line ->
                sb.appendLine("\t${line.replace("|", "¦")}")
            }
        }
        return sb.toString()
    }

    private fun decode(lines: List<String>): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        var current: LogEntry? = null
        val stackLines = mutableListOf<String>()

        fun flush() {
            current?.let {
                entries.add(it.copy(stackTrace = stackLines.joinToString("\n")))
            }
            stackLines.clear()
        }

        for (line in lines) {
            when {
                line.startsWith("\t") -> stackLines.add(line.trimStart())
                line.isNotBlank() -> {
                    flush()
                    current = parseLine(line)
                }
            }
        }
        flush()

        return entries
            .sortedByDescending { it.timestamp }
            .take(MAX_ENTRIES)
    }

    private fun parseLine(line: String): LogEntry? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        return runCatching {
            LogEntry(
                timestamp  = parts[0].toLong(),
                level      = LogLevel.valueOf(parts[1]),
                tag        = parts[2],
                message    = parts[3].replace("¦", "|").replace("↵", "\n"),
            )
        }.getOrNull()
    }

    private fun rotate(file: File) {
        val lines = file.readLines()
        val keep = lines.takeLast(lines.size / 2)
        file.writeText(keep.joinToString("\n") + "\n")
    }
}
