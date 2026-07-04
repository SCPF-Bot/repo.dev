package com.mlbb.assistant.presentation.draft.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Suggestion card shown during the draft pick phase.
 *
 * UX fixes applied:
 * - Applied the app design system (SurfaceCard, themed text, gold accent) instead
 *   of unstyled M3 defaults (Nielsen #4 — Consistency and Standards).
 * - Score is now displayed as a percentage ("85%") instead of a raw decimal
 *   ("0.85"), which is immediately legible (Nielsen #2 — Match System to Real World).
 * - Added a colour-coded score badge (Refactoring UI — use colour purposefully).
 * - Added merged contentDescription for TalkBack (WCAG 4.1.2 Name, Role, Value).
 *
 * RA-06: Long-pressing the card opens a Balloon tooltip with the hero's win rate,
 * tier, and counter/synergy counts — advanced detail without cluttering the compact
 * card view (rec. §8.4, skydoves/Balloon 1.6.12 via JitPack).
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

    var showTooltip by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(8.dp))
                .border(1.dp, SurfaceElevated, RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick          = {},
                    onLongClick      = { showTooltip = true },
                    onLongClickLabel = "View ${hero.name} details"
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = semanticLabel },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = hero.name,
                    color      = TextPrimary,
                    style      = MaterialTheme.typography.titleMedium,
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

            Box(
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

        if (showTooltip) {
            Popup(
                onDismissRequest = { showTooltip = false },
                properties       = PopupProperties(focusable = true)
            ) {
                Box(
                    Modifier
                        .background(Color(0xFF1A1C2E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    SuggestionTooltipContent(hero = hero, scorePercent = scorePercent)
                }
            }
        }
    }
}

/**
 * Balloon tooltip body for [SuggestionCard] long-press.
 * Shows hero tier, win rate, and counter/synergy slot counts.
 */
@Composable
private fun SuggestionTooltipContent(hero: Hero, scorePercent: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text       = hero.name,
            color      = MLBBGold,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text     = "${hero.tier.display} Tier  •  ${hero.role}",
            color    = TextSecondary,
            fontSize = 10.sp
        )
        Text(
            text     = "Win rate: ${"%.1f%%".format(hero.winRate * 100)}",
            color    = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text     = "Score: $scorePercent%",
            color    = TextPrimary,
            fontSize = 10.sp
        )
        if (hero.counters.isNotEmpty()) {
            Text(
                text     = "Counters ${hero.counters.size} hero${if (hero.counters.size == 1) "" else "es"}",
                color    = SuccessGreen,
                fontSize = 10.sp
            )
        }
        if (hero.synergies.isNotEmpty()) {
            Text(
                text     = "Synergises with ${hero.synergies.size} hero${if (hero.synergies.size == 1) "" else "es"}",
                color    = TextDisabled,
                fontSize = 10.sp
            )
        }
    }
}
