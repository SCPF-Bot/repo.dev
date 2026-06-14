package com.mlbb.assistant.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_sessions")
data class DraftSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val rank: String,
    val banTotal: Int,
    val enemyBanIds: List<Int>,      // null entries encoded as -1
    val yourBanIds: List<Int>,
    val enemyPickIds: List<Int>,
    val yourPickIds: List<Int>,
    val ourTeamFirst: Boolean,
    val draftScore: Int,
    val metaScore: Int,
    val counterScore: Int,
    val synergyScore: Int,
    val followedRecommendations: Int,
    val totalRecommendations: Int
)
