package com.mlbb.assistant.domain.usecase

import androidx.paging.PagingData
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * TD-10: Use case that exposes a [PagingData] stream from the hero repository.
 *
 * Usage in a ViewModel:
 * ```kotlin
 * val heroes: Flow<PagingData<Hero>> = getPagedHeroesUseCase()
 *     .cachedIn(viewModelScope)
 * ```
 *
 * `cachedIn` is called at the ViewModel site (not here) so the use case
 * itself remains scope-agnostic and unit-testable.
 *
 * @param query    Name search filter.  Pass empty string for no filter.
 * @param lane     Lane filter.  Pass empty string for all lanes.
 * @param pageSize Number of items per page (default 30 — fits most phone
 *                 screens without excessive over-fetching).
 */
class GetPagedHeroesUseCase @Inject constructor(
    private val repository: HeroRepository
) {
    operator fun invoke(
        query:    String = "",
        lane:     String = "",
        pageSize: Int    = 30
    ): Flow<PagingData<Hero>> = repository.getHeroesPaged(query, lane, pageSize)
}
