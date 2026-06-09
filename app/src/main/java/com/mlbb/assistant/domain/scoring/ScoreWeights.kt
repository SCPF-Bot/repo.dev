package com.mlbb.assistant.domain.scoring

data class ScoreWeights(
    val metaWeight: Double,
    val counterWeight: Double,
    val synergyWeight: Double
)