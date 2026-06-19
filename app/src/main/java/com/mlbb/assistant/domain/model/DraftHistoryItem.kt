package com.mlbb.assistant.domain.model

/**
 * Domain model for a completed draft session as shown in History.
 *
 * Intentionally lighter than [data.local.database.DraftSessionEntity] —
 * the history list only needs the summary scores, not the full slot lists.
 * Slot-level detail can be fetched lazily when the user opens a session.
 */
data class DraftHistoryItem(
    val id: Int = 0,
    val timestamp: Long,
    val rank: String,
    val draftScore: Int,
    val metaScore: Int,
    val counterScore: Int,
    val synergyScore: Int,
    val followedRecommendations: Int,
    val totalRecommendations: Int
) {
    /** Percentage of recommendations the user followed (0–100). */
    val followRate: Int
        get() = if (totalRecommendations == 0) 0
                else (followedRecommendations * 100) / totalRecommendations
}
