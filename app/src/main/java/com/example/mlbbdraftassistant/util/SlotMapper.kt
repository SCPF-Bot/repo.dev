package com.example.mlbbdraftassistant.util

data class SlotInfo(
    val slot: Int,
    val isAlly: Boolean
)

class SlotMapper(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    fun mapBoundingBox(box: BoundingBox): SlotInfo? {
        val cx = box.centerX / screenWidth
        val cy = box.centerY / screenHeight

        // Determine team side
        val isAlly = cx < 0.5f

        // Determine slot based on Y position
        val slot = when {
            cy < 0.16f -> 0  // Ban 1
            cy < 0.24f -> 1  // Ban 2
            cy < 0.34f -> 2  // Pick 1
            cy < 0.48f -> 3  // Pick 2
            cy < 0.62f -> 4  // Pick 3
            cy < 0.76f -> 5  // Pick 4
            cy <= 0.90f -> 6 // Pick 5
            else -> null
        } ?: return null

        return SlotInfo(slot = slot, isAlly = isAlly)
    }
}