package com.mlbb.assistant.domain.advisor

/**
 * Single source of truth for all ban-rate thresholds used across the ban advisor layer.
 *
 * Previously, [BanRecommender.rankSplit] used 0.25 for the "High Ban" badge label
 * while [BanRecommender.buildBanReason] used 0.20 and 0.40 for the same concept —
 * three inconsistent literals with no shared meaning. These constants unify them.
 */
object BanThresholds {
    /** ≥ 20 % ban rate — notable community ban pressure (reason string). */
    const val NOTABLE_BAN_RATE   = 0.20f
    /** ≥ 25 % ban rate — qualifies for the "High Ban" badge label. */
    const val HIGH_BAN_RATE      = 0.25f
    /** ≥ 40 % ban rate — community-consensus absolute ban. */
    const val CONSENSUS_BAN_RATE = 0.40f
}
