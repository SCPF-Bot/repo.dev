package com.mlbb.assistant.presentation.settings

data class SettingsState(
    val metaWeight: Double = 0.5,
    val counterWeight: Double = 0.3,
    val synergyWeight: Double = 0.2,
    val isSaved: Boolean = false
)