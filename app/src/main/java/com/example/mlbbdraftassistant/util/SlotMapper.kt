package com.example.mlbbdraftassistant.util

data class SlotInfo(
    val slot: Int,        // Always 0–4 (pick slot index)
    val isAlly: Boolean
)

/**
 * Maps a bounding box centre to a [SlotInfo] describing which pick slot (0–4)
 * on which team the detection belongs to.
 *
 * FIX: the original implementation returned slots 0–6 (7 slots including two ban
 * rows), while [IconDetector] only handles 5 pick slots (0–4). This caused picks 4
 * and 5 (screen slots 5 and 6) to both be silently clamped to index 4, overwriting
 * valid data.
 *
 * The fix skips the ban-row area (top ~24% of screen) and maps only the five pick
 * rows to indices 0–4.
 */
class SlotMapper(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    fun mapBoundingBox(box: BoundingBox): SlotInfo? {
        val cx = box.centerX / screenWidth
        val cy = box.centerY / screenHeight

        // Determine team side
        val isAlly = cx < 0.5f

        // Skip ban-phase rows (top ~24% of screen); map the 5 pick rows to 0–4
        val slot = when {
            cy < 0.24f -> return null  // Ban rows — not tracked in DetectedDraft
            cy < 0.37f -> 0            // Pick 1
            cy < 0.50f -> 1            // Pick 2
            cy < 0.63f -> 2            // Pick 3
            cy < 0.76f -> 3            // Pick 4
            cy <= 0.90f -> 4           // Pick 5
            else -> return null
        }

        return SlotInfo(slot = slot, isAlly = isAlly)
    }
}
