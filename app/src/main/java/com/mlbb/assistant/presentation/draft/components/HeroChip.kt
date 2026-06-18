package com.mlbb.assistant.presentation.draft.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mlbb.assistant.domain.model.Hero

/**
 * Chip representing a hero that has been added to the draft; tap to remove.
 *
 * UX fixes applied:
 * - Switched from AssistChip → InputChip: InputChip is the correct M3 chip variant
 *   for representing a selected/entered value that can be dismissed. AssistChip is
 *   for suggestions, not removable items (Nielsen #4 — Consistency with standards).
 * - Added trailing Close icon so the remove affordance is explicit — the previous
 *   version had no visual cue that tapping the chip removes it (Nielsen #6 — Recognition
 *   over recall).
 * - Added semantic [contentDescription] so TalkBack announces "Remove [Hero], button"
 *   rather than just the hero name (WCAG 4.1.2 Name, Role, Value).
 */
@Composable
fun HeroChip(
    hero: Hero,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    InputChip(
        selected      = true,
        onClick       = onRemove,
        label         = { Text(hero.name) },
        trailingIcon  = {
            Icon(
                imageVector        = Icons.Rounded.Close,
                contentDescription = null
            )
        },
        modifier = modifier.semantics {
            contentDescription = "Remove ${hero.name}"
        }
    )
}
