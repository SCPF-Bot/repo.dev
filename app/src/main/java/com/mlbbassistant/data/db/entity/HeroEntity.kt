package com.mlbbassistant.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mlbbassistant.data.model.Hero
import com.mlbbassistant.data.model.HeroLane
import com.mlbbassistant.data.model.HeroRole

@Entity(tableName = "heroes")
data class HeroEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")           val id: Int,
    @ColumnInfo(name = "name")         val name: String,
    @ColumnInfo(name = "role")         val role: String,
    @ColumnInfo(name = "secondary_role") val secondaryRole: String?,
    @ColumnInfo(name = "win_rate")     val winRate: Float,
    @ColumnInfo(name = "pick_rate")    val pickRate: Float,
    @ColumnInfo(name = "ban_rate")     val banRate: Float,
    @ColumnInfo(name = "counters")     val counters: List<Int>,
    @ColumnInfo(name = "countered_by") val counteredBy: List<Int>,
    @ColumnInfo(name = "synergies")    val synergies: List<Int>,
    @ColumnInfo(name = "lane")         val lane: String,
    @ColumnInfo(name = "image_url")    val imageUrl: String
) {
    /** Safe conversion — never throws, falls back to UNKNOWN/JUNGLE on bad strings. */
    fun toDomain(): Hero = Hero(
        id            = id,
        name          = name.ifBlank { "Hero $id" },
        role          = HeroRole.fromString(role),
        secondaryRole = secondaryRole?.let { HeroRole.fromString(it) }
                            ?.takeIf { it != HeroRole.UNKNOWN },
        winRate       = winRate.coerceIn(0f, 1f),
        pickRate      = pickRate.coerceIn(0f, 1f),
        banRate       = banRate.coerceIn(0f, 1f),
        counters      = counters,
        counteredBy   = counteredBy,
        synergies     = synergies,
        lane          = HeroLane.fromString(lane),
        imageUrl      = imageUrl
    )
}
