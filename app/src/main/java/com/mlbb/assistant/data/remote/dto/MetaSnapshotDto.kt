// File: app/src/main/java/com/mlbb/assistant/data/remote/dto/MetaSnapshotDto.kt
package com.mlbb.assistant.data.remote.dto

data class MetaSnapshotDto(
    val heroes: List<HeroDto>
)

data class HeroDto(
    val id: Int,
    val name: String,
    val role: String,
    val winRate: Double,
    val pickRate: Double,
    val banRate: Double,
    val imageUrl: String,
    val counters: List<Int>,
    val synergies: List<Int>
)