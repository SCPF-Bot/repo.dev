package com.mlbb.assistant.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the hero counter-confidence lookup table.
 *
 * The table is seeded from [counter_lookup.json] (sourced from community
 * research, credit: bipash25/mlbb-assistant) which provides empirically
 * derived win-rate-based counter confidence values between hero pairs.
 *
 * Counter confidence values are directional:
 *  - `countersConfidence("Saber", "Layla")` = 0.657 means Saber wins against
 *    Layla 65.7% of the time — a strong counter relationship.
 *  - Pairs not present in the dataset have confidence = 0 (no established data).
 */
@Dao
interface CounterLookupDao {

    @Query("SELECT confidence FROM counter_lookup WHERE hero_name = :hero AND counters_hero = :target LIMIT 1")
    suspend fun getCounterConfidence(hero: String, target: String): Float?

    @Query("SELECT * FROM counter_lookup WHERE hero_name = :hero ORDER BY confidence DESC")
    suspend fun getCountersFor(hero: String): List<CounterLookupEntity>

    @Query("SELECT * FROM counter_lookup WHERE counters_hero = :hero ORDER BY confidence DESC")
    suspend fun getWeakAgainst(hero: String): List<CounterLookupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CounterLookupEntity>)

    @Query("SELECT COUNT(*) FROM counter_lookup")
    suspend fun count(): Int

    @Query("DELETE FROM counter_lookup")
    suspend fun deleteAll()
}
