package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.DraftHistoryItem
import com.mlbb.assistant.domain.model.DraftOutcome

/**
 * Derives actionable patterns from a player's draft history.
 *
 * Patterns surfaced:
 * - Win-rate at different recommendation-follow rates.
 * - Average score breakdown across wins vs. losses.
 * - Most-improved dimension (meta / counter / synergy) on won drafts.
 * - Streak analysis (current win/loss run).
 */
object DraftPatternAnalyzer {

    data class DraftInsights(
        val totalSessions: Int,
        val winRate: Float,                     // 0..1 across known-outcome sessions
        val followRateWins: Float,              // avg recommendation-follow rate on wins
        val followRateLosses: Float,            // avg recommendation-follow rate on losses
        val avgScoreWins: Float,                // avg overall draftScore on wins
        val avgScoreLosses: Float,              // avg overall draftScore on losses
        val currentStreak: String,             // e.g. "3 Win streak" or "2 Loss streak"
        val topInsight: String                  // primary actionable finding
    )

    fun analyze(history: List<DraftHistoryItem>): DraftInsights? {
        if (history.isEmpty()) return null

        val labelled  = history.filter { it.outcome != DraftOutcome.UNKNOWN }
        val wins      = labelled.filter { it.outcome == DraftOutcome.WIN }
        val losses    = labelled.filter { it.outcome == DraftOutcome.LOSS }
        val winRate   = if (labelled.isEmpty()) 0f else wins.size.toFloat() / labelled.size

        val followRateWins   = if (wins.isEmpty()) 0f else wins.map { followRate(it) }.average().toFloat()
        val followRateLosses = if (losses.isEmpty()) 0f else losses.map { followRate(it) }.average().toFloat()

        val avgScoreWins   = if (wins.isEmpty()) 0f else wins.map { it.draftScore.toFloat() }.average().toFloat()
        val avgScoreLosses = if (losses.isEmpty()) 0f else losses.map { it.draftScore.toFloat() }.average().toFloat()

        val streak = computeStreak(history)
        val insight = buildTopInsight(winRate, followRateWins, followRateLosses, avgScoreWins, avgScoreLosses, labelled.size)

        return DraftInsights(
            totalSessions    = history.size,
            winRate          = winRate,
            followRateWins   = followRateWins,
            followRateLosses = followRateLosses,
            avgScoreWins     = avgScoreWins,
            avgScoreLosses   = avgScoreLosses,
            currentStreak    = streak,
            topInsight       = insight
        )
    }

    private fun followRate(item: DraftHistoryItem): Float =
        if (item.totalRecommendations == 0) 0f
        else item.followedRecommendations.toFloat() / item.totalRecommendations

    private fun computeStreak(history: List<DraftHistoryItem>): String {
        val sorted = history
            .filter { it.outcome != DraftOutcome.UNKNOWN }
            .sortedByDescending { it.timestamp }
        if (sorted.isEmpty()) return "No recorded outcomes yet"

        val first = sorted.first().outcome
        val count = sorted.takeWhile { it.outcome == first }.size
        return "$count ${first.display} streak"
    }

    private fun buildTopInsight(
        winRate: Float, followWin: Float, followLoss: Float,
        scoreWin: Float, scoreLoss: Float, labelledCount: Int
    ): String {
        if (labelledCount < 5) return "Play ${5 - labelledCount} more rated drafts to unlock insights."

        return when {
            followWin - followLoss >= 0.20f ->
                "Following recommendations is strongly correlated with your wins — keep trusting the engine."
            followLoss - followWin >= 0.20f ->
                "You win more when you deviate from suggestions — your instincts are sharper than the model on this account."
            winRate >= 0.60f ->
                "Strong win rate (%.0f%%) — the current weight settings are working well for you.".format(winRate * 100)
            winRate <= 0.35f ->
                "Low win rate (%.0f%%) — consider letting the calibrator adjust your weights.".format(winRate * 100)
            scoreWin - scoreLoss >= 5f ->
                "Higher-scoring drafts win more often — focus on quality over speed during selection."
            else ->
                "Moderate win rate (%.0f%%) — consistent performance. Keep recording outcomes.".format(winRate * 100)
        }
    }
}
