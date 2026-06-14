package com.mlbb.assistant.data.remote.dto

import com.mlbb.assistant.data.local.database.HeroEntity
import com.mlbb.assistant.domain.model.CoreItem

data class MetaSnapshotDto(val heroes: List<HeroDto>)

data class HeroDto(
    val id: Int,
    val name: String,
    val role: String,
    val secondaryRole: String? = null,
    val lane: String,
    val tier: String,
    val patchTrend: Double = 0.0,
    val winRate: Double,
    val pickRate: Double,
    val banRate: Double,
    val imageUrl: String,
    val counters: List<Int> = emptyList(),
    val counteredBy: List<Int> = emptyList(),
    val synergies: List<Int> = emptyList(),
    val recommendedSpells: List<String> = emptyList(),
    val coreItems: List<CoreItem> = emptyList(),
    val flexLanes: List<String> = emptyList(),
    val isToxicMechanic: Boolean = false,
    val isOP: Boolean = false
) {
    fun toEntity() = HeroEntity(
        id = id, name = name, role = role,
        secondaryRole = secondaryRole, lane = lane, tier = tier,
        patchTrend = patchTrend, winRate = winRate, pickRate = pickRate,
        banRate = banRate, imageUrl = imageUrl, counters = counters,
        counteredBy = counteredBy, synergies = synergies,
        recommendedSpells = recommendedSpells, coreItems = coreItems,
        flexLanes = flexLanes, isToxicMechanic = isToxicMechanic, isOP = isOP
    )
}
