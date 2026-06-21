package com.mlbb.assistant.utils

import android.content.Context
import android.widget.Toast

/**
 * Shows a short Toast. Call from a coroutine or click handler on the Main dispatcher,
 * NOT directly inside a @Composable function body (side effects belong in LaunchedEffect).
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

// ── Collection extensions ─────────────────────────────────────────────────────

/**
 * Returns `null` when the list is empty, or the list itself when it has elements.
 * Useful for `?: fallback` chains that want to treat empty and null uniformly.
 */
fun <T> List<T>.nullIfEmpty(): List<T>? = ifEmpty { null }

/**
 * Returns the average of the selected [selector] values, or [default] when the
 * collection is empty (instead of throwing [NoSuchElementException] / NaN).
 */
inline fun <T> Iterable<T>.averageOrDefault(default: Double = 0.0, selector: (T) -> Double): Double {
    var sum   = 0.0
    var count = 0
    for (element in this) { sum += selector(element); count++ }
    return if (count == 0) default else sum / count
}

/**
 * Partitions the list into two lists: the first contains elements for which
 * [predicate] is true, the second for which it is false — typed as a
 * destructurable `Pair` for concise call sites.
 *
 * Example:
 *   val (wins, losses) = sessions.partitionBy { it.outcome == DraftOutcome.WIN }
 */
inline fun <T> List<T>.partitionBy(predicate: (T) -> Boolean): Pair<List<T>, List<T>> =
    partition(predicate)

// ── String extensions ─────────────────────────────────────────────────────────

/**
 * Capitalises the first character and lower-cases the rest, like
 * "MARKSMAN" → "Marksman".  Safe on empty strings.
 */
fun String.toTitleCase(): String =
    if (isEmpty()) this else this[0].uppercaseChar() + substring(1).lowercase()

/**
 * Returns `null` when the string is blank, or the trimmed string otherwise.
 * Useful for optional fields that may arrive as `"  "` from JSON.
 */
fun String.blankToNull(): String? = trim().ifBlank { null }

// ── Float / percentage helpers ────────────────────────────────────────────────

/**
 * Formats a 0–1 ratio as a percentage string, e.g. `0.527` → `"52.7 %"`.
 * The optional [decimals] parameter controls how many decimal places are shown.
 */
fun Float.toPercentString(decimals: Int = 1): String =
    "%.${decimals}f %%".format(this * 100f)

/**
 * Formats a 0–1 ratio as a whole-number percentage, e.g. `0.527` → `"53%"`.
 */
fun Double.toWholePercentString(): String = "%.0f%%".format(this * 100.0)
