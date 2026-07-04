package com.mlbb.assistant.utils

/**
 * Returns the average of the selected [selector] values, or [default] when the
 * collection is empty (instead of throwing [NoSuchElementException] / NaN).
 *
 * Pure-Kotlin extension — lives in :core:scoring so domain logic
 * (DraftScoreCalculator, CompositionAnalyzer) can call it without depending
 * on the data layer.
 */
inline fun <T> Iterable<T>.averageOrDefault(default: Double = 0.0, selector: (T) -> Double): Double {
    var sum   = 0.0
    var count = 0
    for (element in this) { sum += selector(element); count++ }
    return if (count == 0) default else sum / count
}
