package com.mlbb.assistant.domain.engine

enum class TeamSide { OUR_TEAM, ENEMY_TEAM }

data class PickTurn(
    val index: Int,           // 0-based pick index (0..9)
    val side: TeamSide,
    val isDoublePick: Boolean,
    val isFirstPick: Boolean,
    val isLastPick: Boolean,
    val pickNumber: Int       // 1-based display number
)

object PickSequenceEngine {

    /**
     * MLBB pick sequence: 1–2–2–2–2–1
     * Indices 0–9, where each entry is the TeamSide that picks.
     *
     * If OUR_TEAM picks first:
     *   US, THEM, THEM, US, US, THEM, THEM, US, US, THEM
     * If ENEMY_TEAM picks first:
     *   THEM, US, US, THEM, THEM, US, US, THEM, THEM, US
     */
    fun buildSequence(firstPicker: TeamSide): List<PickTurn> {
        // Raw ownership pattern: [0]=first, [1-2]=second, [3-4]=first, [5-6]=second, [7-8]=first, [9]=second
        val pattern = listOf(
            firstPicker,
            firstPicker.opponent(), firstPicker.opponent(),
            firstPicker,            firstPicker,
            firstPicker.opponent(), firstPicker.opponent(),
            firstPicker,            firstPicker,
            firstPicker.opponent()
        )

        return pattern.mapIndexed { i, side ->
            PickTurn(
                index        = i,
                side         = side,
                isDoublePick = i in 1..8 && pattern[if (i % 2 == 1) i + 1 else i - 1] == side,
                isFirstPick  = i == 0,
                isLastPick   = i == 9,
                pickNumber   = i + 1
            )
        }
    }

    fun getCurrentTurn(sequence: List<PickTurn>, index: Int): PickTurn? =
        sequence.getOrNull(index)

    private fun TeamSide.opponent() =
        if (this == TeamSide.OUR_TEAM) TeamSide.ENEMY_TEAM else TeamSide.OUR_TEAM
}
