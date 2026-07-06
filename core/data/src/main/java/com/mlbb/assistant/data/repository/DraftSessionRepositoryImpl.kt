package com.mlbb.assistant.data.repository

import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.domain.repository.DraftSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DraftSessionRepositoryImpl @Inject constructor(
    private val dao: DraftSessionDao
) : DraftSessionRepository {

    override fun getAllSessions(): Flow<List<DraftHistoryItem>> =
        dao.getAllSessions().map { it.map(DraftSessionEntity::toDomain) }

    override fun getRecentSessions(limit: Int): Flow<List<DraftHistoryItem>> =
        dao.getRecentSessions(limit).map { it.map(DraftSessionEntity::toDomain) }

    override suspend fun getSessionById(id: Int): DraftHistoryItem? =
        dao.getSessionById(id)?.toDomain()

    override suspend fun saveSession(item: DraftHistoryItem): Long =
        dao.insert(item.toEntity())

    override suspend fun deleteSession(id: Int) =
        dao.deleteById(id)

    override suspend fun deleteAllSessions() =
        dao.deleteAll()
}

// ── Mapping extensions ────────────────────────────────────────────────────────

private fun DraftSessionEntity.toDomain() = DraftHistoryItem(
    id                      = id,
    timestamp               = timestamp,
    rank                    = rank,
    draftScore              = draftScore,
    metaScore               = metaScore,
    counterScore            = counterScore,
    synergyScore            = synergyScore,
    followedRecommendations = followedRecommendations,
    totalRecommendations    = totalRecommendations,
    outcome                 = DraftOutcome.fromString(outcome),
    isSimulation            = isSimulation,
    yourPickIds             = yourPickIds,
    ourTeamFirst            = ourTeamFirst,
    enemyBanIds             = enemyBanIds,
    yourBanIds              = yourBanIds,
    enemyPickIds            = enemyPickIds
)

private fun DraftHistoryItem.toEntity() = DraftSessionEntity(
    id                      = id,
    timestamp               = timestamp,
    rank                    = rank,
    banTotal                = enemyBanIds.count { it >= 0 } + yourBanIds.count { it >= 0 },
    enemyBanIds             = enemyBanIds,
    yourBanIds              = yourBanIds,
    enemyPickIds            = enemyPickIds,
    yourPickIds             = yourPickIds,
    ourTeamFirst            = ourTeamFirst,
    draftScore              = draftScore,
    metaScore               = metaScore,
    counterScore            = counterScore,
    synergyScore            = synergyScore,
    followedRecommendations = followedRecommendations,
    totalRecommendations    = totalRecommendations,
    outcome                 = outcome.name,
    isSimulation            = isSimulation
)
