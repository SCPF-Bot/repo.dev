package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Proficiency
import com.mlbb.assistant.domain.scoring.DraftScorer
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.domain.scoring.ScoreWeights
import javax.inject.Inject

class GetSuggestionsUseCase @Inject constructor() {
    /**
     * Returns a ranked list of hero recommendations for the current draft state.
     *
     * @param pool     All available heroes (not yet banned or picked).
     * @param session  Current in-memory draft state.
     * @param weights  Scoring weights; defaults to [ScoreWeights.DEFAULT].
     * @param poolMap  Personal hero pool: maps hero ID → player's proficiency.
     *                 When non-empty, un-pooled heroes are downweighted via
     *                 [Proficiency.NONE.scoreMultiplier] (TD-02).
     */
    operator fun invoke(
        pool: List<Hero>,
        session: DraftSession,
        weights: ScoreWeights = ScoreWeights.DEFAULT,
        poolMap: Map<Int, Proficiency> = emptyMap()
    ): List<HeroScore> = DraftScorer.rankAll(
        pool         = pool,
        alliedPicks  = session.ourPickedHeroes,
        enemyPicks   = session.enemyPickedHeroes,
        bannedIds    = session.unavailableIds,
        weights      = weights,
        currentTurn  = session.currentTurn,
        pickIndex    = session.currentPickIndex,
        maxPickIndex = 10,
        poolMap      = poolMap
    )
}
