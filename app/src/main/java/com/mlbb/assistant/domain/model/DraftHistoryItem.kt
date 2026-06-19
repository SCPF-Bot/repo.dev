package com.mlbb.assistant.domain.model

/**
 * Pure domain representation of a saved draft session.
 * Keeps the presentation layer independent of Room entities.
 */
data class DraftHistoryItem(
    val id: Int,
    val timestamp: Long,
    val rank: String,
    val draftScore: Int,
    val metaScore: Int,
    val counterScore: Int,
    val synergyScore: Int,
    val followedRecommendations: Int,
    val totalRecommendations: Int
)
