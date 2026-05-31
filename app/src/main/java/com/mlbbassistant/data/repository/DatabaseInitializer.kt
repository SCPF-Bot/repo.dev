package com.mlbbassistant.data.repository

import com.mlbbassistant.data.db.dao.HeroDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Called once at app startup (from [com.mlbbassistant.MLBBApp]) to insert
 * seed heroes when the database is empty.
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    private val heroDao: HeroDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfEmpty() {
        scope.launch {
            if (heroDao.getAll().isEmpty()) {
                heroDao.upsertAll(SeedDataProvider.heroes())
            }
        }
    }
}
