// File: app/src/main/java/com/mlbb/assistant/domain/model/Hero.kt
package com.mlbb.assistant.domain.model

data class Hero(
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