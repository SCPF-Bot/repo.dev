package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory state machine for an active draft session.
 *
 * Owned by the DI graph as a singleton so [OverlayService] and
 * [presentation.draft.DraftViewModel] share the same state.
 *
 * All mutations are thread-safe via [MutableStateFlow].
 */
class DraftSessionManager {

    // ── Phase ─────────────────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(DraftPhase.IDLE)
    val phase: StateFlow<DraftPhase> = _phase.asStateFlow()

    fun advancePhase(next: DraftPhase) {
        _phase.value = next
    }

    // ── Bans ──────────────────────────────────────────────────────────────────

    private val _enemyBans = MutableStateFlow<List<Hero?>>(List(5) { null })
    val enemyBans: StateFlow<List<Hero?>> = _enemyBans.asStateFlow()

    private val _ourBans = MutableStateFlow<List<Hero?>>(List(5) { null })
    val ourBans: StateFlow<List<Hero?>> = _ourBans.asStateFlow()

    fun setEnemyBan(slot: Int, hero: Hero?) {
        _enemyBans.update { list -> list.toMutableList().also { it[slot] = hero } }
    }

    fun setOurBan(slot: Int, hero: Hero?) {
        _ourBans.update { list -> list.toMutableList().also { it[slot] = hero } }
    }

    // ── Picks ─────────────────────────────────────────────────────────────────

    private val _enemyPicks = MutableStateFlow<List<Hero?>>(List(5) { null })
    val enemyPicks: StateFlow<List<Hero?>> = _enemyPicks.asStateFlow()

    private val _ourPicks = MutableStateFlow<List<Hero?>>(List(5) { null })
    val ourPicks: StateFlow<List<Hero?>> = _ourPicks.asStateFlow()

    fun setEnemyPick(slot: Int, hero: Hero?) {
        _enemyPicks.update { list -> list.toMutableList().also { it[slot] = hero } }
    }

    fun setOurPick(slot: Int, hero: Hero?) {
        _ourPicks.update { list -> list.toMutableList().also { it[slot] = hero } }
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    private val _ourTeamFirst = MutableStateFlow(true)
    val ourTeamFirst: StateFlow<Boolean> = _ourTeamFirst.asStateFlow()

    fun setOurTeamFirst(value: Boolean) {
        _ourTeamFirst.value = value
    }

    private val _rank = MutableStateFlow("Warrior")
    val rank: StateFlow<String> = _rank.asStateFlow()

    fun setRank(value: String) {
        _rank.value = value
    }

    // ── Convenience getters ───────────────────────────────────────────────────

    val bannedIds: Set<Int>
        get() = (_enemyBans.value + _ourBans.value).mapNotNull { it?.id }.toSet()

    val pickedIds: Set<Int>
        get() = (_enemyPicks.value + _ourPicks.value).mapNotNull { it?.id }.toSet()

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        _phase.value       = DraftPhase.IDLE
        _enemyBans.value   = List(5) { null }
        _ourBans.value     = List(5) { null }
        _enemyPicks.value  = List(5) { null }
        _ourPicks.value    = List(5) { null }
        _ourTeamFirst.value = true
        _rank.value        = "Warrior"
    }
}
