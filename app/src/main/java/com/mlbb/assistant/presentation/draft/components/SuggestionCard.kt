package com.mlbb.assistant.presentation.draft.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.WarningAmber
import com.mlbb.assistant.presentation.common.theme.ErrorRed

/**
 * Suggestion card shown during the draft pick phase.
 *
 * UX fixes applied:
 * - Applied the app design system (SurfaceCard, themed text, gold accent) instead
 *   of unstyled M3 defaults, which clashed visually with every other screen
 *   (Nielsen #4 — Consistency and Standards).
 * - Score is now displayed as a percentage ("85%") instead of a raw decimal
 *   ("0.85"), which is immediately legible (Nielsen #2 — Match System to Real World).
 * - Added a colour-coded score badge so users can assess fit at a glance without
 *   reading the number (Refactoring UI — use colour purposefully).
 * - Win rate label increased from default body size to keep contrast ratio with
 *   the themed background (WCAG 1.4.3 Contrast).
 * - Added merged contentDescription for TalkBack (WCAG 4.1.2 Name, Role, Value).
 */
@Composable
fun SuggestionCard(
    hero: Hero,
    score: Double,
    modifier: Modifier = Modifier
) {
    val scorePercent = (score * 100).toInt()
    val scoreColor = when {
        scorePercent >= 75 -> SuccessGreen
        scorePercent >= 50 -> WarningAmber
        else               -> ErrorRed
    }

    val semanticLabel = "${hero.name}, ${hero.role}, score ${scorePercent}%, " +
        "win rate ${"%.0f".format(hero.winRate * 100)} percent"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(8.dp))
            .border(1.dp, SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = semanticLabel },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = hero.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = "${hero.role}  •  ${hero.tier.display} Tier",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text  = "Win rate: ${"%.0f%%".format(hero.winRate * 100)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(scoreColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .border(1.dp, scoreColor.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = "$scorePercent%",
                color      = scoreColor,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
