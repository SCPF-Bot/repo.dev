package com.example.mlbbdraftassistant.util

import com.example.mlbbdraftassistant.data.model.Hero

object FuzzyMatcher {

    private const val SIMILARITY_THRESHOLD = 0.75

    /**
     * Finds the best matching hero for a recognised text string.
     * Returns null if no match exceeds the similarity threshold.
     *
     * FIX: OCR often returns multi-line text (hero name + role label on the
     * same crop). The original code matched the whole raw string, producing a
     * low similarity score even when the first line was a perfect hero name.
     * We now try each line independently and keep the best match across all lines.
     */
    fun match(recognizedText: String, heroes: List<Hero>): Hero? {
        val lines = recognizedText
            .lines()
            .map { it.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "") }
            .filter { it.length >= 2 }

        if (lines.isEmpty()) return null

        var bestHero: Hero? = null
        var bestScore = 0.0

        for (line in lines) {
            for (hero in heroes) {
                val score = similarity(line, hero.normalizedName)
                if (score > bestScore) {
                    bestScore = score
                    bestHero = hero
                }
            }
        }

        return if (bestScore >= SIMILARITY_THRESHOLD) bestHero else null
    }

    /**
     * Normalised similarity score between 0.0 (completely different) and 1.0 (identical).
     * Uses a Levenshtein‑based ratio.
     */
    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val distance = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length).toDouble()
        return 1.0 - (distance / maxLen)
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        var prevRow = IntArray(len2 + 1) { it }
        var currRow = IntArray(len2 + 1)

        for (i in 1..len1) {
            currRow[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                currRow[j] = minOf(
                    currRow[j - 1] + 1,       // insertion
                    prevRow[j] + 1,           // deletion
                    prevRow[j - 1] + cost     // substitution
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        return prevRow[len2]
    }
}
