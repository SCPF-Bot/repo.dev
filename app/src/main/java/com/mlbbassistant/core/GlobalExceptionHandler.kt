package com.mlbbassistant.core

import android.content.Context
import android.util.Log
import kotlin.system.exitProcess

/**
 * Last-resort uncaught-exception handler.
 * Logs the crash, then delegates to the system default handler so Android
 * still shows its standard crash dialog / restarts the app.
 */
class GlobalExceptionHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e("GlobalExceptionHandler",
                "Uncaught exception on thread ${thread.name}", throwable)
            // Persist a lightweight crash marker so the next launch can show a
            // "previous session crashed" banner if desired.
            context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_crash", throwable.message?.take(500) ?: "unknown")
                .putLong("last_crash_ts", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
            // Never let the handler itself throw
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
                ?: exitProcess(1)
        }
    }
}
