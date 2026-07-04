package com.mlbb.assistant.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mlbb.assistant.domain.model.Proficiency

/**
 * Room entity for the personal hero pool.
 *
 * Each row records a single hero the user has added to their pool and their
 * self-assessed proficiency level.  Heroes absent from this table are treated
 * as [Proficiency.NONE] by the scoring engine when the pool is non-empty.
 *
 * Added in DB schema version 3.
 */
@Entity(tableName = "hero_pool")
data class HeroPoolEntity(
    @PrimaryKey val heroId: Int,
    /**
     * Stored as the [Proficiency] enum name so future enum entries do not
     * require a schema migration (analogous to [DraftSessionEntity.outcome]).
     */
    val proficiency: String = Proficiency.COMFORTABLE.name
) {
    fun toProficiency(): Proficiency = Proficiency.fromString(proficiency)
}
