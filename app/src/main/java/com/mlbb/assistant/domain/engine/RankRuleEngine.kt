package com.mlbb.assistant.domain.engine

enum class Rank(val display: String) {
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
        Rank.EPIC -> BanStructure(
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
        Rank.UNKNOWN -> BanStructure(
            totalBans = 6, round1PerTeam = 3, round2PerTeam = 0, hasRound2 = false
        )
    }

    /** S1–S5 slots that are designated banners at Epic rank */
    fun getBannerSlots(rank: Rank): List<Int> = when (rank) {
        Rank.EPIC -> listOf(3, 4, 5) // S3, S4, S5
        else      -> listOf(1, 2, 3, 4, 5) // all players can ban
    }

    fun fromString(raw: String): Rank {
        val s = raw.trim().lowercase()
        return when {
            s.contains("immortal")         -> Rank.IMMORTAL
            s.contains("glory")            -> Rank.MYTHICAL_GLORY
            s.contains("honor")            -> Rank.MYTHICAL_HONOR
            s.contains("mythic")           -> Rank.MYTHIC
            s.contains("legend")           -> Rank.LEGEND
            s.contains("epic")             -> Rank.EPIC
            else                           -> Rank.UNKNOWN
        }
    }

    /** Fallback: infer rank from observed ban count */
    fun inferFromBanCount(observedBans: Int): Rank = when {
        observedBans >= 10 -> Rank.MYTHIC
        observedBans >= 8  -> Rank.LEGEND
        else               -> Rank.EPIC
    }
}
