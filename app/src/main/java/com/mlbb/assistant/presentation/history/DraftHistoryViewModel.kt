package com.mlbb.assistant.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DraftHistoryViewModel @Inject constructor(
    draftSessionDao: DraftSessionDao
) : ViewModel() {

    val sessions: StateFlow<List<DraftSessionEntity>> =
        draftSessionDao.getRecentSessions()
            .stateIn(
                scope            = viewModelScope,
                started          = SharingStarted.WhileSubscribed(5_000L),
                initialValue     = emptyList()
            )
}
