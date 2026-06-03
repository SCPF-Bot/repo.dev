package com.mlbbassistant.data.api.dto

import com.mlbbassistant.data.db.entity.HeroEntity
import com.mlbbassistant.data.db.entity.MetaSnapshotEntity

fun HeroDto.toEntity(): HeroEntity = HeroEntity(
    id            = id,
    name          = name.trim().ifBlank { "Hero $id" },
    role          = role.trim().ifBlank { "UNKNOWN" },
    secondaryRole = secondaryRole?.trim()?.ifBlank { null },
    winRate       = winRate.coerceIn(0f, 1f),
    pickRate      = pickRate.coerceIn(0f, 1f),
    banRate       = banRate.coerceIn(0f, 1f),
    counters      = counters,
    counteredBy   = counteredBy,
    synergies     = synergies,
    lane          = lane.trim().ifBlank { "JUNGLE" },
    imageUrl      = imageUrl.trim()
)

/** Top-level function so DatabaseInitializer can call it without an import alias. */
fun toEntity(dto: HeroDto): HeroEntity = dto.toEntity()

fun MetaSnapshotDto.toEntity(): MetaSnapshotEntity = MetaSnapshotEntity(
    patch     = patch.trim().ifBlank { "unknown" },
    updatedAt = updatedAt,
    fetchedAt = System.currentTimeMillis()
)
