package com.mlbb.assistant.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Room entity representing a directional hero counter relationship with a
 * confidence score derived from win-rate data.
 *
 * Source: [counter_lookup.json] — community dataset aggregated from MLBB
 * match statistics (credit: bipash25/mlbb-assistant, open-source).
 *
 * Schema is intentionally minimal: the primary key is the composite of
 * (hero_name, counters_hero) so upserts are safe without a separate id.
 *
 * Example row: heroName="Saber", countersHero="Layla", confidence=0.657
 * means Saber beats Layla in ~65.7% of matchups.
 */
@Entity(
    tableName = "counter_lookup",
    primaryKeys = ["hero_name", "counters_hero"]
)
data class CounterLookupEntity(
    @ColumnInfo(name = "hero_name")     val heroName: String,
    @ColumnInfo(name = "counters_hero") val countersHero: String,
    @ColumnInfo(name = "confidence")    val confidence: Float,
    /** Direction: "counters" = hero beats target, "weak_against" = target beats hero. */
    @ColumnInfo(name = "direction")     val direction: String = "counters"
)
