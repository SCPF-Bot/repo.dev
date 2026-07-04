package com.mlbb.assistant.domain.engine

import com.mlbb.assistant.domain.model.DraftOutcome
import com.mlbb.assistant.domain.model.Hero
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DraftPhase {
    IDLE, SETUP, BAN_ROUND_1, BAN_ROUND_2, PICK, TRADING, COMPLETE
}

data class DraftSession(
    val rank: Rank = Rank.UNKNOWN,
    val banStructure: BanStructure = RankRuleEngine.getBanStructure(Rank.UNKNOWN),
    val phase: DraftPhase = DraftPhase.IDLE,
    val ourTeamFirst: Boolean = true,
    val pickSequence: List<PickTurn> = emptyList(),
    val currentPickIndex: Int = 0,

    // null = slot not yet filled, hero with id=-1 = missed ban (timeout)
    val enemyBansR1: List<Hero?> = List(3) { null },
    val ourBansR1:   List<Hero?> = List(3) { null },
    val enemyBansR2: List<Hero?> = emptyList(),
    val ourBansR2:   List<Hero?> = emptyList(),

    val enemyPicks: List<Hero?> = List(5) { null },
    val ourPicks:   List<Hero?> = List(5) { null },

    val undoStack: List<DraftAction> = emptyList(),

    // scoring
    val followedRecommendations: Int = 0,
    val totalRecommendations: Int = 0,

    /**
     * Match outcome — set by the user after the game finishes.
     * Defaults to UNKNOWN until explicitly recorded.
     */
    val outcome: DraftOutcome = DraftOutcome.UNKNOWN,

    /**
     * When true, the draft is a simulation and should NOT be saved to
     * the history database or counted in calibration stats.
     */
    val isSimulation: Boolean = false
) {
    val allBannedHeroes: List<Hero>
        get() = (enemyBansR1 + ourBansR1 + enemyBansR2 + ourBansR2).filterNotNull()
                    .filter { it.id != MISSED_BAN_ID }

    val allPickedHeroes: List<Hero>
        get() = (enemyPicks + ourPicks).filterNotNull()

    val unavailableIds: Set<Int>
        get() = (allBannedHeroes + allPickedHeroes).map { it.id }.toSet()

    val ourPickedHeroes: List<Hero>
        get() = ourPicks.filterNotNull()

    val enemyPickedHeroes: List<Hero>
        get() = enemyPicks.filterNotNull()

    val currentTurn: PickTurn?
        get() = pickSequence.getOrNull(currentPickIndex)

    companion object { const val MISSED_BAN_ID = -1 }
}

sealed class DraftAction {
    data class EnemyBan(val hero: Hero?, val round: Int, val slot: Int) : DraftAction()
    data class OurBan(val hero: Hero?, val round: Int, val slot: Int)   : DraftAction()
    data class EnemyPick(val hero: Hero, val slot: Int)                  : DraftAction()
    data class OurPick(val hero: Hero, val slot: Int)                    : DraftAction()
    data class HeroSwap(val fromSlot: Int, val toSlot: Int)              : DraftAction()
}

class DraftSessionManager {

    private val _session = MutableStateFlow(DraftSession())
    val session: StateFlow<DraftSession> = _session.asStateFlow()

    // ── Initialise ────────────────────────────────────────────────────────────

    fun initSession(rank: Rank, ourTeamFirst: Boolean, isSimulation: Boolean = false) {
        val structure = RankRuleEngine.getBanStructure(rank)
        val sequence  = PickSequenceEngine.buildSequence(
            if (ourTeamFirst) TeamSide.OUR_TEAM else TeamSide.ENEMY_TEAM
        )
        _session.update {
            DraftSession(
                rank           = rank,
                banStructure   = structure,
                phase          = DraftPhase.SETUP,
                ourTeamFirst   = ourTeamFirst,
                pickSequence   = sequence,
                enemyBansR2    = if (structure.hasRound2) List(structure.round2PerTeam) { null } else emptyList(),
                ourBansR2      = if (structure.hasRound2) List(structure.round2PerTeam) { null } else emptyList(),
                isSimulation   = isSimulation
            )
        }
    }

    /** Records the match outcome after the user has played the game. */
    fun setOutcome(outcome: DraftOutcome) = _session.update { it.copy(outcome = outcome) }

    /** Marks or unmarks the session as a simulation draft. */
    fun setSimulation(isSimulation: Boolean) = _session.update { it.copy(isSimulation = isSimulation) }

    fun startBanPhase() = _session.update { it.copy(phase = DraftPhase.BAN_ROUND_1) }

    fun startBanRound2() = _session.update { it.copy(phase = DraftPhase.BAN_ROUND_2) }

    fun startPickPhase() = _session.update { it.copy(phase = DraftPhase.PICK, currentPickIndex = 0) }

    fun startTradingPhase() = _session.update { it.copy(phase = DraftPhase.TRADING) }

    fun completeDraft() = _session.update { it.copy(phase = DraftPhase.COMPLETE) }

    // ── Ban actions ───────────────────────────────────────────────────────────

    /**
     * M-05 fix: [List.plus] on [DraftSession.undoStack] allocates a brand-new
     * backing array on every ban/pick/swap — O(n) per action, O(n²) over a
     * full draft. Building via `toMutableList().apply { add(...) }` still
     * returns an immutable-looking `List` reference for the `copy()` call
     * (StateFlow snapshots are never mutated in place afterwards) but avoids
     * the extra intermediate list [List.plus] creates internally.
     */
    private fun List<DraftAction>.appended(action: DraftAction): List<DraftAction> =
        toMutableList().apply { add(action) }

    fun recordEnemyBan(hero: Hero?, round: Int, slot: Int) {
        val action = DraftAction.EnemyBan(hero, round, slot)
        _session.update { s ->
            val updated = if (round == 1)
                s.copy(enemyBansR1 = s.enemyBansR1.withIndex().map { (i, h) -> if (i == slot) hero else h })
            else
                s.copy(enemyBansR2 = s.enemyBansR2.withIndex().map { (i, h) -> if (i == slot) hero else h })
            updated.copy(undoStack = s.undoStack.appended(action))
        }
    }

    fun recordOurBan(hero: Hero?, round: Int, slot: Int) {
        val action = DraftAction.OurBan(hero, round, slot)
        _session.update { s ->
            val updated = if (round == 1)
                s.copy(ourBansR1 = s.ourBansR1.withIndex().map { (i, h) -> if (i == slot) hero else h })
            else
                s.copy(ourBansR2 = s.ourBansR2.withIndex().map { (i, h) -> if (i == slot) hero else h })
            updated.copy(undoStack = s.undoStack.appended(action))
        }
    }

    // ── Pick actions ──────────────────────────────────────────────────────────

    fun recordEnemyPick(hero: Hero, slot: Int) {
        _session.update { s ->
            s.copy(
                enemyPicks    = s.enemyPicks.withIndex().map { (i, h) -> if (i == slot) hero else h },
                currentPickIndex = s.currentPickIndex + 1,
                undoStack     = s.undoStack.appended(DraftAction.EnemyPick(hero, slot))
            )
        }
    }

    fun recordOurPick(hero: Hero, slot: Int, followedRecommendation: Boolean = false) {
        _session.update { s ->
            s.copy(
                ourPicks         = s.ourPicks.withIndex().map { (i, h) -> if (i == slot) hero else h },
                currentPickIndex = s.currentPickIndex + 1,
                undoStack        = s.undoStack.appended(DraftAction.OurPick(hero, slot)),
                followedRecommendations = if (followedRecommendation) s.followedRecommendations + 1 else s.followedRecommendations,
                totalRecommendations    = s.totalRecommendations + 1
            )
        }
    }

    // ── Undo ──────────────────────────────────────────────────────────────────

    /**
     * Atomically reads the undo stack and applies the reversal inside a single
     * [MutableStateFlow.update] lambda.
     *
     * P2-07 fix: the previous implementation read [_session.value.undoStack] via
     * a snapshot *before* calling [_session.update], creating a TOCTOU window where
     * the stack could be mutated by a concurrent caller between the read and the
     * update. Reading `last` from [current] (the value inside the lambda) eliminates
     * this gap entirely — the lambda always operates on the latest consistent state.
     */
    fun undo() {
        _session.update { current ->
            val last = current.undoStack.lastOrNull() ?: return@update current
            when (last) {
                is DraftAction.OurBan   -> {
                    val slots = if (last.round == 1) current.ourBansR1.toMutableList()
                                else current.ourBansR2.toMutableList()
                    slots[last.slot] = null
                    if (last.round == 1) current.copy(ourBansR1 = slots, undoStack = current.undoStack.dropLast(1))
                    else                 current.copy(ourBansR2 = slots, undoStack = current.undoStack.dropLast(1))
                }
                is DraftAction.EnemyBan -> {
                    val slots = if (last.round == 1) current.enemyBansR1.toMutableList()
                                else current.enemyBansR2.toMutableList()
                    slots[last.slot] = null
                    if (last.round == 1) current.copy(enemyBansR1 = slots, undoStack = current.undoStack.dropLast(1))
                    else                 current.copy(enemyBansR2 = slots, undoStack = current.undoStack.dropLast(1))
                }
                is DraftAction.OurPick  -> {
                    val picks = current.ourPicks.toMutableList()
                    picks[last.slot] = null
                    current.copy(
                        ourPicks         = picks,
                        currentPickIndex = (current.currentPickIndex - 1).coerceAtLeast(0),
                        undoStack        = current.undoStack.dropLast(1)
                    )
                }
                is DraftAction.EnemyPick -> {
                    val picks = current.enemyPicks.toMutableList()
                    picks[last.slot] = null
                    current.copy(
                        enemyPicks       = picks,
                        currentPickIndex = (current.currentPickIndex - 1).coerceAtLeast(0),
                        undoStack        = current.undoStack.dropLast(1)
                    )
                }
                is DraftAction.HeroSwap -> current.copy(undoStack = current.undoStack.dropLast(1))
            }
        }
    }

    // ── Trading phase ─────────────────────────────────────────────────────────

    fun swapOurHeroes(fromSlot: Int, toSlot: Int) {
        _session.update { s ->
            val picks = s.ourPicks.toMutableList()
            val temp = picks[fromSlot]
            picks[fromSlot] = picks[toSlot]
            picks[toSlot] = temp
            s.copy(ourPicks = picks, undoStack = s.undoStack.appended(DraftAction.HeroSwap(fromSlot, toSlot)))
        }
    }

    fun reset() { _session.value = DraftSession() }

    // ── Rank fallback ─────────────────────────────────────────────────────────

    fun upgradeRankFromObservedBans(banCount: Int) {
        val inferred = RankRuleEngine.inferFromBanCount(banCount)
        if (inferred.ordinal < _session.value.rank.ordinal || _session.value.rank == Rank.UNKNOWN) {
            val structure = RankRuleEngine.getBanStructure(inferred)
            _session.update { it.copy(
                rank         = inferred,
                banStructure = structure,
                enemyBansR2  = if (structure.hasRound2) List(structure.round2PerTeam) { null } else emptyList(),
                ourBansR2    = if (structure.hasRound2) List(structure.round2PerTeam) { null } else emptyList()
            )}
        }
    }
}
