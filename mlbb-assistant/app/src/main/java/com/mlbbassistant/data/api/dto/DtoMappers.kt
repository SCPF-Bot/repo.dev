package com.mlbbassistant.data.api.dto

import com.mlbbassistant.data.db.entity.HeroEntity
import com.mlbbassistant.data.db.entity.MetaSnapshotEntity

fun HeroDto.toEntity(): HeroEntity = HeroEntity(
    id           = id,
    name         = name,
    role         = role,
    secondaryRole = secondaryRole,
    winRate      = winRate,
    pickRate     = pickRate,
    banRate      = banRate,
    counters     = counters,
    counteredBy  = counteredBy,
    synergies    = synergies,
    lane         = lane,
    imageUrl     = imageUrl
)

fun MetaSnapshotDto.toEntity(): MetaSnapshotEntity = MetaSnapshotEntity(
    patch     = patch,
    updatedAt = updatedAt,
    fetchedAt = System.currentTimeMillis()
)
