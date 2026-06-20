package com.mlbb.assistant.domain.model

/**
 * Match outcome recorded after a draft session is completed.
 * Stored as a string column in [DraftSessionEntity] so future
 * enum entries do not require a schema migration.
 */
enum class DraftOutcome(val display: String, val emoji: String) {
    WIN("Win", "🏆"),
    LOSS("Loss", "💀"),
    UNKNOWN("Unknown", "❓");

    companion object {
        fun fromString(value: String): DraftOutcome =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
