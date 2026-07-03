package com.mlbb.assistant.capture

import java.util.concurrent.ConcurrentHashMap

/**
 * Temporal consensus over the last few frames per slot (recommendations.md §5.4).
 *
 * This sits alongside (not instead of) [PortraitMatcher]'s existing
 * `consecutiveHits` confirmation counter: `consecutiveHits` requires the *same*
 * heroId on N consecutive calls, which is brittle to a single noisy frame
 * resetting the streak. [SlotConsensusManager] instead keeps a short rolling
 * window and confirms on a plurality vote, so one dropped/misclassified frame
 * doesn't reset progress toward confirmation.
 */
class SlotConsensusManager(
    private val windowSize: Int = PhaseDetectionConfig.CONSENSUS_WINDOW_SIZE,
    private val minAgreement: Int = PhaseDetectionConfig.CONSENSUS_MIN_AGREEMENT
) {

    private data class Vote(val heroId: Int, val confidence: Float)

    private val windows = ConcurrentHashMap<String, ArrayDeque<Vote>>()

    /** Records a new observation for [slotKey]. Null [heroId] clears no history — it's simply not counted. */
    fun update(slotKey: String, heroId: Int?, confidence: Float) {
        if (heroId == null) return
        val window = windows.getOrPut(slotKey) { ArrayDeque() }
        synchronized(window) {
            window.addLast(Vote(heroId, confidence))
            while (window.size > windowSize) window.removeFirst()
        }
    }

    /**
     * Returns the confirmed (heroId, meanConfidence) pair if the plurality winner
     * in [slotKey]'s window has at least [minAgreement] votes, else null.
     */
    fun confirm(slotKey: String): Pair<Int, Float>? {
        val window = windows[slotKey] ?: return null
        val snapshot = synchronized(window) { window.toList() }
        if (snapshot.isEmpty()) return null

        val grouped = snapshot.groupBy { it.heroId }
        val (winnerId, votes) = grouped.maxByOrNull { it.value.size } ?: return null
        if (votes.size < minAgreement) return null

        val meanConfidence = votes.map { it.confidence }.average().toFloat()
        return winnerId to meanConfidence
    }

    /** Clears history for a single slot (e.g. once it's been recorded as filled). */
    fun clear(slotKey: String) {
        windows.remove(slotKey)
    }

    /** Clears all slot history — call at session start / phase change. */
    fun clearAll() {
        windows.clear()
    }
}
