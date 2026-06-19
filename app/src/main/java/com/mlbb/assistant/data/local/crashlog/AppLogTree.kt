package com.mlbb.assistant.data.local.crashlog

import android.content.Context
import android.util.Log
import timber.log.Timber

/**
 * Timber tree that persists WARN and ERROR logs (plus the custom CRASH pseudo-level)
 * to [CrashLogStore].  DEBUG / INFO logs are intentionally excluded to keep the
 * file small and relevant.
 *
 * Plant this in [com.mlbb.assistant.MLBBApplication.onCreate] alongside the
 * standard [Timber.DebugTree].
 */
class AppLogTree(private val context: Context) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.WARN  -> LogLevel.WARN
            Log.ERROR -> LogLevel.ERROR
            Log.ASSERT -> LogLevel.CRASH
            else      -> LogLevel.WARN
        }

        val entry = LogEntry(
            timestamp  = System.currentTimeMillis(),
            level      = level,
            tag        = tag ?: "Unknown",
            message    = message,
            stackTrace = t?.stackTraceToString() ?: ""
        )

        CrashLogStore.appendSync(context, entry)
    }
}

/**
 * Installs a global [Thread.UncaughtExceptionHandler] that writes the crash to
 * [CrashLogStore] **before** delegating to the default handler (which kills the
 * process).  Call this once in [com.mlbb.assistant.MLBBApplication.onCreate].
 */
fun installCrashHandler(context: Context) {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            val entry = LogEntry(
                timestamp  = System.currentTimeMillis(),
                level      = LogLevel.CRASH,
                tag        = "UncaughtException[${thread.name}]",
                message    = throwable.message ?: throwable.javaClass.simpleName,
                stackTrace = throwable.stackTraceToString()
            )
            CrashLogStore.appendSync(context, entry)
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
