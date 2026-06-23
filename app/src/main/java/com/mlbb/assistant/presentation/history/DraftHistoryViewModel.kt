package com.mlbb.assistant.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.usecase.GetDraftHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Draft History screen.
 *
 * Delegates to [GetDraftHistoryUseCase] through the domain layer, keeping
 * the presentation layer independent of Room DAOs.  The use case goes through
 * [com.mlbb.assistant.data.repository.DraftSessionRepositoryImpl] which
 * correctly maps all entity fields — including [DraftHistoryItem.yourPickIds] —
 * so no local mapping extension is needed here.
 */
@HiltViewModel
class DraftHistoryViewModel @Inject constructor(
    getDraftHistoryUseCase: GetDraftHistoryUseCase
) : ViewModel() {

    val sessions: StateFlow<List<DraftHistoryItem>> =
        getDraftHistoryUseCase()
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )
}
