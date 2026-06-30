package com.mlbb.assistant.presentation.draft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.components.HeroPortrait

@Composable
fun DraftScreen(viewModel: DraftViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "DRAFT PLANNER",
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style      = MaterialTheme.typography.titleLarge
        )
        Text(
            "Use the floating overlay during MLBB draft for the full experience. " +
            "This view shows the current session state.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        val hasSessionData = state.allies.isNotEmpty() ||
                             state.enemies.isNotEmpty() ||
                             state.suggestions.isNotEmpty()

        if (!hasSessionData) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Groups,
                        contentDescription = "No active draft",
                        tint               = MaterialTheme.colorScheme.outlineVariant,
                        modifier           = Modifier.size(56.dp)
                    )
                    Text(
                        "No active draft session",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Start a draft from the Home screen to see live suggestions here.",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            if (state.allies.isNotEmpty()) {
                Text(
                    "YOUR TEAM",
                    color      = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allies.take(5).forEach { hero ->
                        HeroPortrait(hero = hero, size = 48.dp, showName = true)
                    }
                }
            }

            if (state.enemies.isNotEmpty()) {
                Text(
                    "ENEMY TEAM",
                    color      = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.enemies.take(5).forEach { hero ->
                        HeroPortrait(hero = hero, size = 48.dp, showName = true)
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                Text(
                    "TOP SUGGESTIONS",
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.labelMedium
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.suggestions.take(5), key = { it.hero.id }) { heroScore ->
                        SuggestionCard(heroScore = heroScore)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(heroScore: HeroScore) {
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            HeroPortrait(hero = heroScore.hero, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    heroScore.hero.name,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.bodyMedium
                )
                Text(
                    heroScore.reason,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    style  = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    heroScore.badgeLabel,
                    color  = MaterialTheme.colorScheme.primary,
                    style  = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "%.0f%%".format(heroScore.totalScore * 100),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
