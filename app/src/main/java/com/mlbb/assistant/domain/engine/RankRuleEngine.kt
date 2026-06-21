package com.mlbb.assistant.domain.engine

/**
 * All rank tiers recognised by the app.
 *
 * Tiers are ordered from lowest (WARRIOR) to highest (IMMORTAL), followed by
 * UNKNOWN as a sentinel.  The ordinal values are used by
 * [DraftSessionManager.upgradeRankFromObservedBans] to determine whether an
 * inferred rank should replace the current one.
 *
 * Note: WARRIOR/ELITE/MASTER are treated identically to EPIC for ban-structure
 * purposes (6 total bans, no round 2) because they share the same in-game
 * draft rules.
 */
enum class Rank(val display: String) {
    WARRIOR("Warrior"),
    ELITE("Elite"),
    MASTER("Master"),
    EPIC("Epic"),
    LEGEND("Legend"),
    MYTHIC("Mythic"),
    MYTHICAL_HONOR("Mythical Honor"),
    MYTHICAL_GLORY("Mythical Glory"),
    IMMORTAL("Immortal"),
    UNKNOWN("Unknown")
}

data class BanStructure(
    val totalBans: Int,
    val round1PerTeam: Int,
    val round2PerTeam: Int,
    val hasRound2: Boolean
) {
    val round1Total get() = round1PerTeam * 2
    val round2Total get() = round2PerTeam * 2
}

object RankRuleEngine {

    fun getBanStructure(rank: Rank): BanStructure = when (rank) {
        Rank.WARRIOR,
        Rank.ELITE,
        Rank.MASTER,
        Rank.EPIC,
        Rank.UNKNOWN -> BanStructure(
            totalBans = 6, round1PerTeam = 3, round2PerTeam = 0, hasRound2 = false
        )
        Rank.LEGEND -> BanStructure(
            totalBans = 8, round1PerTeam = 3, round2PerTeam = 1, hasRound2 = true
        )
        Rank.MYTHIC,
        Rank.MYTHICAL_HONOR,
        Rank.MYTHICAL_GLORY,
        Rank.IMMORTAL -> BanStructure(
            totalBans = 10, round1PerTeam = 3, round2PerTeam = 2, hasRound2 = true
        )
    }

    /** S1–S5 slots that are designated banners per rank tier. */
    fun getBannerSlots(rank: Rank): List<Int> = when (rank) {
        Rank.WARRIOR,
        Rank.ELITE,
        Rank.MASTER,
        Rank.EPIC -> listOf(3, 4, 5) // S3, S4, S5 only
        else      -> listOf(1, 2, 3, 4, 5) // all players can ban
    }

    /**
     * Parses a freeform rank string (e.g. from OCR or user input) into a [Rank].
     * Matching is case-insensitive and tolerates common suffixes like "I", "IV", "III".
     */
    fun fromString(raw: String): Rank {
        val s = raw.trim().lowercase()
        return when {
            s.contains("immortal")         -> Rank.IMMORTAL
            s.contains("glory")            -> Rank.MYTHICAL_GLORY
            s.contains("honor")            -> Rank.MYTHICAL_HONOR
            s.contains("mythic")           -> Rank.MYTHIC
            s.contains("legend")           -> Rank.LEGEND
            s.contains("epic")             -> Rank.EPIC
            s.contains("master")           -> Rank.MASTER
            s.contains("elite")            -> Rank.ELITE
            s.contains("warrior")          -> Rank.WARRIOR
            else                           -> Rank.UNKNOWN
        }
    }

    /** Fallback: infer rank tier from the number of observed bans in the lobby. */
    fun inferFromBanCount(observedBans: Int): Rank = when {
        observedBans >= 10 -> Rank.MYTHIC
        observedBans >= 8  -> Rank.LEGEND
        else               -> Rank.EPIC
    }
}
