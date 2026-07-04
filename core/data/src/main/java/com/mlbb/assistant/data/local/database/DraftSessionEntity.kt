package com.mlbb.assistant.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mlbb.assistant.domain.model.DraftOutcome

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
    val totalRecommendations: Int,
    /**
     * Match outcome recorded by the user post-game.
     * Stored as the enum name string so new entries do not require a migration.
     * Added in DB schema v3 — defaults to "UNKNOWN" via migration.
     */
    val outcome: String = DraftOutcome.UNKNOWN.name,
    /**
     * True when the draft was run in simulation mode.
     * Added in DB schema v3 — defaults to 0 (false) via migration.
     */
    val isSimulation: Boolean = false
)
