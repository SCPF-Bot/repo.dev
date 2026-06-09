// File: app/src/main/java/com/mlbb/assistant/domain/scoring/ScoreWeights.kt
package com.mlbb.assistant.domain.scoring

data class ScoreWeights(
    val metaWeight: Double,
    val counterWeight: Double,
    val synergyWeight: Double
)