package com.example.mlbbdraftassistant.domain

data class ScoringConfig(
    val synergyWeight: Float = 0.30f,
    val counterWeight: Float = 0.40f,
    val roleWeight: Float = 0.10f,
    val metaWeight: Float = 0.20f
) {
    companion object {
        val DEFAULT = ScoringConfig()
    }
}