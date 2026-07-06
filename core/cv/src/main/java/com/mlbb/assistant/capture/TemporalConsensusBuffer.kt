package com.mlbb.assistant.capture

import java.util.concurrent.ConcurrentHashMap

/**
 * 15-frame / 500 ms sliding-window consensus buffer for [HeroPortraitObjectDetector]
 * detections (master plan Phase 2, "TemporalConsensusBuffer").
 *
 * ### This class vs. [SlotConsensusManager]
 *
 * [SlotConsensusManager] answers "which *hero* is confirmed in a slot we already
 * know is filled" (portrait-identity consensus, driven by [PortraitMatcher]).
 *
 * This buffer answers a cheaper, earlier question: "is this slot *actually
 * occupied right now*, according to the raw YOLO bounding-box stream?" A
 * detector can flicker frame-to-frame during the hero-reveal fly-in animation
 * or a brief occlusion — requiring [minAgreementRatio] agreement across a short
 * time window filters that noise out before the expensive portrait-classification
 * step ([PortraitMatcher.match]) ever runs on a spuriously "filled" slot.
 *
 * Thread-safety: [ConcurrentHashMap] guards the per-slot map; each individual
 * per-slot deque is additionally synchronized because [ArrayDeque] itself is not
 * thread-safe and frame analysis runs on a single [kotlinx.coroutines.Dispatchers.Default]
 * worker at a time, but callers should not assume that invariant will always hold.
 */
class TemporalConsensusBuffer(
    private val maxSamples: Int = PhaseDetectionConfig.YOLO_CONSENSUS_WINDOW_SIZE,
    private val windowMs: Long = PhaseDetectionConfig.YOLO_CONSENSUS_WINDOW_MS,
    private val minAgreementRatio: Float = PhaseDetectionConfig.YOLO_CONSENSUS_MIN_RATIO,
) {

    /** One observation: a slot was seen as [label] (or `null`/"empty" sentinel) at [timestampMs]. */
    private data class Sample(val timestampMs: Long, val label: String)

    private val windows = ConcurrentHashMap<String, ArrayDeque<Sample>>()

    companion object {
        /** Sentinel label recorded when a frame's detection pass found a matching box for a slot. */
        const val LABEL_FILLED = "__filled__"

        /** Sentinel label recorded when a frame's detection pass found no matching box for a slot. */
        const val LABEL_EMPTY = "__empty__"
    }

    /**
     * Records one frame's observation for [slotKey].
     *
     * Callers should record every frame they run detection on — including
     * "not detected this frame" (pass [LABEL_EMPTY]) — so the consensus ratio
     * reflects true detector stability, not just a count of positive hits.
     */
    fun record(slotKey: String, label: String, timestampMs: Long = System.currentTimeMillis()) {
        val window = windows.getOrPut(slotKey) { ArrayDeque() }
        synchronized(window) {
            window.addLast(Sample(timestampMs, label))
            while (window.size > maxSamples) window.removeFirst()
            while (window.isNotEmpty() && timestampMs - window.first().timestampMs > windowMs) {
                window.removeFirst()
            }
        }
    }

    /**
     * Returns the majority label for [slotKey] if it holds at least
     * [minAgreementRatio] of the samples currently in the window, else `null`
     * (no consensus yet — caller should fall back to the coordinate-based
     * heuristic in [SlotRegions]/[OverlayCaptureCoordinator.isSlotFilled]).
     *
     * Returns `null` (not [LABEL_EMPTY]) both when the window is empty and when
     * the window's majority *is* [LABEL_EMPTY] but under the ratio — callers that
     * care about the "confidently empty" case should compare the return value to
     * [LABEL_EMPTY] explicitly.
     */
    fun consensus(slotKey: String): String? {
        val window = windows[slotKey] ?: return null
        val snapshot = synchronized(window) { window.toList() }
        if (snapshot.isEmpty()) return null
        val (label, votes) = snapshot
            .groupingBy { it.label }
            .eachCount()
            .maxByOrNull { it.value }
            ?: return null
        return if (votes / snapshot.size.toFloat() >= minAgreementRatio) label else null
    }

    /** True once [slotKey] has enough samples to make [consensus] meaningful. */
    fun hasSamples(slotKey: String): Boolean = windows[slotKey]?.isNotEmpty() == true

    fun clear(slotKey: String) {
        windows.remove(slotKey)
    }

    fun clearAll() {
        windows.clear()
    }
}
