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
    val formattedTime: String get() = DateFormatter.formatLog(timestamp)
}

/**
 * Append-only log store backed by a plain text file in the app's private files
 * directory. Each entry is written as a fixed-format line:
 *
 *   TIMESTAMP|LEVEL|TAG|MESSAGE
 *
 * Multi-line stack-traces are written as additional lines each prefixed with TAB
 * and collected back onto the preceding entry on read.
 *
 * The file is capped at [MAX_BYTES] to prevent unbounded growth, and rotation
 * additionally caps the *line count* at [MAX_LOG_LINES] (L-06/UX-06): a burst
 * of many small single-line entries can stay under the byte cap for a long
 * time while still holding far more history than [MAX_ENTRIES] ever surfaces
 * on read, so the line-count cap keeps [rotate] from being byte-cap-starved.
 * Thread-safety on [appendSync] is provided by [lock] so concurrent
 * crash-handler calls on different threads cannot interleave bytes.
 */
object CrashLogStore {

    private const val LOG_FILE_NAME = "mlbb_crash_log.txt"
    private const val MAX_BYTES     = 512 * 1024L  // 512 KB
    private const val MAX_LOG_LINES = 4000          // hard cap independent of byte size
    private const val MAX_ENTRIES   = 500

    private val lock = Any()

    private fun logFile(context: Context): File =
        File(context.filesDir, LOG_FILE_NAME)

    // ── Write ─────────────────────────────────────────────────────────────────

    fun appendSync(context: Context, entry: LogEntry) {
        synchronized(lock) {
            runCatching {
                val file = logFile(context)
                if (file.exists() && (file.length() > MAX_BYTES || file.readLines().size >= MAX_LOG_LINES)) {
                    rotate(file)
                }
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
            current?.let { entries.add(it.copy(stackTrace = stackLines.joinToString("\n"))) }
            stackLines.clear()
        }

        for (line in lines) {
            when {
                line.startsWith("\t") -> stackLines.add(line.trimStart())
                line.isNotBlank()     -> { flush(); current = parseLine(line) }
            }
        }
        flush()

        return entries.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
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
        // Halve on byte-cap rotation as before, but never keep more than
        // MAX_LOG_LINES regardless of how few bytes those lines occupy.
        val halved = lines.takeLast(lines.size / 2)
        val keep   = halved.takeLast(MAX_LOG_LINES)
        file.writeText(keep.joinToString("\n") + "\n")
    }
}
