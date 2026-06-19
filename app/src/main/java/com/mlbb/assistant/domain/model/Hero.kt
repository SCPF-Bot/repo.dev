package com.mlbb.assistant.domain.model

import androidx.compose.runtime.Stable

/**
 * Pure domain model representing an MLBB hero with current-patch meta data.
 *
 * Annotated [@Stable] so Compose can skip recompositions when the instance
 * has not changed. All properties are immutable [val], satisfying the
 * stability contract without needing [@Immutable].
 */
@Stable
data class Hero(
    val id: Int,
    val name: String,
    val role: String,
    val secondaryRole: String?,
    val lane: Lane,
    val tier: Tier,
    val patchTrend: Double,
    val winRate: Double,
    val pickRate: Double,
    val banRate: Double,
    val imageUrl: String,
    val counters: List<Int>,
    val counteredBy: List<Int>,
    val synergies: List<Int>,
    val recommendedSpells: List<String>,
    val coreItems: List<CoreItem>,
    val flexLanes: List<Lane>,
    val isToxicMechanic: Boolean,
    val isOP: Boolean
)
