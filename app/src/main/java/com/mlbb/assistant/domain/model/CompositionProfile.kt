package com.mlbb.assistant.domain.model

import androidx.compose.runtime.Stable

/**
 * High-level summary of a team's composition, used to drive pick suggestions
 * and overlay commentary during the draft.
 *
 * Annotated [@Stable] so Compose skips recomposition when the instance is
 * unchanged (all properties are immutable [val]).
 */
@Stable
data class CompositionProfile(
    /** Dominant damage type of the team (Physical / Magical / Mixed). */
    val damageType: DamageType,
    /** Whether the composition has reliable crowd-control. */
    val hasCrowdControl: Boolean,
    /** Whether the composition has a reliable initiator. */
    val hasInitiator: Boolean,
    /** Whether the composition has a sustain healer/shielder. */
    val hasSustain: Boolean,
    /** Whether the composition can reliably split-push. */
    val hasSplitPush: Boolean,
    /** Lanes that are currently unoccupied / not yet picked. */
    val openLanes: List<Lane>
)

enum class DamageType { PHYSICAL, MAGICAL, MIXED }
