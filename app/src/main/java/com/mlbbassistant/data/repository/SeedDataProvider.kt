package com.mlbbassistant.data.repository

import com.mlbbassistant.data.db.entity.HeroEntity

/**
 * Provides a static list of heroes used to seed the database on first launch,
 * so the app is usable offline before the user ever hits the network.
 *
 * All win/pick/ban rates are illustrative. Counter/synergy lists use the IDs
 * defined below. Replace with live data once [HeroRepository.refreshHeroes] succeeds.
 */
object SeedDataProvider {

    fun heroes(): List<HeroEntity> = listOf(
        // ── Tanks / Roamers ──────────────────────────────────────────────────
        hero(1,  "Tigreal",   "TANK",    null,        .50f, .08f, .05f, listOf(5, 7), listOf(10, 12), listOf(2, 3), "ROAM"),
        hero(2,  "Khufra",    "TANK",    null,        .52f, .12f, .30f, listOf(9, 11), listOf(4, 6), listOf(1, 3), "ROAM"),
        hero(3,  "Atlas",     "TANK",    null,        .53f, .14f, .28f, listOf(7, 8),  listOf(5, 10), listOf(1, 2), "ROAM"),
        hero(4,  "Franco",    "TANK",    null,        .48f, .10f, .08f, listOf(6, 12), listOf(2, 9),  listOf(1, 5), "ROAM"),
        // ── Fighters ─────────────────────────────────────────────────────────
        hero(5,  "Paquito",   "FIGHTER", null,        .54f, .18f, .40f, listOf(1, 3), listOf(7, 8),   listOf(6, 9),  "EXP"),
        hero(6,  "Esmeralda", "FIGHTER", "MAGE",      .52f, .15f, .22f, listOf(4, 3), listOf(5, 10),  listOf(2, 7),  "EXP"),
        hero(7,  "Yu Zhong",  "FIGHTER", null,        .53f, .20f, .35f, listOf(1, 4), listOf(5, 11),  listOf(8, 9),  "EXP"),
        hero(8,  "Thamuz",    "FIGHTER", null,        .51f, .11f, .10f, listOf(6, 3), listOf(7, 12),  listOf(5, 9),  "EXP"),
        // ── Assassins ────────────────────────────────────────────────────────
        hero(9,  "Ling",      "ASSASSIN", null,       .52f, .22f, .45f, listOf(2, 4), listOf(5, 10),  listOf(11, 15), "JUNGLE"),
        hero(10, "Lancelot",  "ASSASSIN", null,       .50f, .20f, .38f, listOf(3, 6), listOf(7, 8),   listOf(9, 15),  "JUNGLE"),
        hero(11, "Hayabusa",  "ASSASSIN", null,       .51f, .16f, .25f, listOf(4, 2), listOf(9, 12),  listOf(10, 14), "JUNGLE"),
        hero(12, "Gusion",    "ASSASSIN", "MAGE",     .51f, .17f, .28f, listOf(1, 3), listOf(11, 6),  listOf(9, 13),  "JUNGLE"),
        // ── Mages ────────────────────────────────────────────────────────────
        hero(13, "Pharsa",    "MAGE",    null,        .52f, .14f, .20f, listOf(7, 5), listOf(9, 11),  listOf(14, 16), "MID"),
        hero(14, "Lylia",     "MAGE",    null,        .53f, .12f, .18f, listOf(8, 4), listOf(10, 12), listOf(13, 16), "MID"),
        hero(15, "Cecilion",  "MAGE",    null,        .54f, .10f, .15f, listOf(5, 3), listOf(9, 11),  listOf(13, 17), "MID"),
        hero(16, "Vale",      "MAGE",    null,        .51f, .13f, .12f, listOf(7, 8), listOf(12, 10), listOf(15, 14), "MID"),
        // ── Marksmen ─────────────────────────────────────────────────────────
        hero(17, "Beatrix",   "MARKSMAN", null,       .52f, .18f, .32f, listOf(6, 8), listOf(9, 11),  listOf(18, 3),  "GOLD"),
        hero(18, "Wanwan",    "MARKSMAN", null,       .54f, .20f, .42f, listOf(7, 5), listOf(10, 12), listOf(17, 2),  "GOLD"),
        hero(19, "Brody",     "MARKSMAN", null,       .53f, .16f, .25f, listOf(3, 6), listOf(9, 11),  listOf(17, 20), "GOLD"),
        hero(20, "Melissa",   "MARKSMAN", null,       .52f, .14f, .18f, listOf(2, 4), listOf(5, 7),   listOf(19, 18), "GOLD"),
        // ── Supports ─────────────────────────────────────────────────────────
        hero(21, "Estes",     "SUPPORT", null,        .51f, .10f, .08f, listOf(5, 9), listOf(10, 12), listOf(22, 1),  "ROAM"),
        hero(22, "Floryn",    "SUPPORT", null,        .53f, .12f, .16f, listOf(7, 8), listOf(11, 4),  listOf(21, 3),  "ROAM"),
        hero(23, "Angela",    "SUPPORT", null,        .52f, .15f, .20f, listOf(3, 6), listOf(9, 10),  listOf(18, 17), "ROAM"),
        hero(24, "Mathilda",  "SUPPORT", "ASSASSIN",  .54f, .17f, .30f, listOf(2, 4), listOf(5, 7),   listOf(22, 19), "ROAM")
    )

    private fun hero(
        id: Int, name: String, role: String, secondaryRole: String?,
        winRate: Float, pickRate: Float, banRate: Float,
        counters: List<Int>, counteredBy: List<Int>, synergies: List<Int>,
        lane: String
    ) = HeroEntity(
        id           = id,
        name         = name,
        role         = role,
        secondaryRole = secondaryRole,
        winRate      = winRate,
        pickRate     = pickRate,
        banRate      = banRate,
        counters     = counters,
        counteredBy  = counteredBy,
        synergies    = synergies,
        lane         = lane,
        imageUrl     = ""
    )
}
