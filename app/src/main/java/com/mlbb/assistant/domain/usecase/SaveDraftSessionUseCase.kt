package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.repository.DraftSessionRepository
import javax.inject.Inject

/**
 * Use case: persist a completed draft session to local storage.
 *
 * Previously the [data.local.database.DraftSessionDao] was defined but never
 * called — completed sessions were silently discarded. This use case closes
 * that gap by calling [DraftSessionRepository.saveSession] at draft completion.
 *
 * Called from [presentation.draft.DraftViewModel] when phase reaches
 * [domain.engine.DraftPhase.COMPLETE].
 *
 * @return The auto-generated row ID of the persisted record (≥ 1), or -1 on failure.
 */
class SaveDraftSessionUseCase @Inject constructor(
    private val repository: DraftSessionRepository
) {
    suspend operator fun invoke(item: DraftHistoryItem): Long =
        runCatching { repository.saveSession(item) }
            .getOrDefault(-1L)
}
