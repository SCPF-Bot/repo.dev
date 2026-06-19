package com.mlbb.assistant.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.DraftHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DraftHistoryViewModel @Inject constructor(
    draftSessionDao: DraftSessionDao
) : ViewModel() {

    val sessions: StateFlow<List<DraftHistoryItem>> =
        draftSessionDao.getRecentSessions()
            .map { entities -> entities.map { it.toDomain() } }
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )
}

private fun DraftSessionEntity.toDomain() = DraftHistoryItem(
    id                      = id,
    timestamp               = timestamp,
    rank                    = rank,
    draftScore              = draftScore,
    metaScore               = metaScore,
    counterScore            = counterScore,
    synergyScore            = synergyScore,
    followedRecommendations = followedRecommendations,
    totalRecommendations    = totalRecommendations
)
