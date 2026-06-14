package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.DraftScorer
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.domain.scoring.ScoreWeights
import javax.inject.Inject

class GetSuggestionsUseCase @Inject constructor() {
    operator fun invoke(
        pool: List<Hero>,
        session: DraftSession,
        weights: ScoreWeights = ScoreWeights.DEFAULT
    ): List<HeroScore> = DraftScorer.rankAll(
        pool        = pool,
        alliedPicks = session.ourPickedHeroes,
        enemyPicks  = session.enemyPickedHeroes,
        bannedIds   = session.unavailableIds,
        weights     = weights,
        currentTurn = session.currentTurn
    )
}
