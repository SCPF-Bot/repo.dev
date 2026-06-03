package com.mlbbassistant.data.repository

import android.util.Log
import com.mlbbassistant.data.api.dto.toEntity
import com.mlbbassistant.data.db.dao.HeroDao
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseInitializer @Inject constructor(
    private val heroDao: HeroDao,
    private val assetDataSource: AssetHeroDataSource
) {
    companion object { private const val TAG = "DatabaseInitializer" }

    private val handler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Seed coroutine crashed", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    fun seedIfEmpty() {
        scope.launch {
            try {
                if (heroDao.getAll().isNotEmpty()) {
                    Log.d(TAG, "DB already populated — skipping seed")
                    return@launch
                }
                // 1. Try asset data source
                val entities = assetDataSource.load()
                    ?.heroes
                    ?.map { it.toEntity() }
                    ?.takeIf { it.isNotEmpty() }
                // 2. Fall back to hardcoded seed
                    ?: SeedDataProvider.heroes().also {
                        Log.w(TAG, "Asset load failed — using SeedDataProvider")
                    }
                heroDao.upsertAll(entities)
                Log.d(TAG, "Seeded ${entities.size} heroes")
            } catch (e: Exception) {
                Log.e(TAG, "Seed failed — app will work with empty hero list", e)
            }
        }
    }
}
