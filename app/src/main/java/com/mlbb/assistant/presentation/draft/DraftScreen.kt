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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary

@Composable
fun DraftScreen(viewModel: DraftViewModel = hiltViewModel()) {
    // Pass 4 / UX fix: collectAsStateWithLifecycle — lifecycle-aware flow collection.
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pass 1 (UX fix): removed emoji "⚔️" — emoji in Text composables renders
        // inconsistently across Android versions and violates the app's icon-only convention.
        Text("DRAFT PLANNER", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Use the floating overlay during MLBB draft for the full experience. " +
            "This view shows the current session state.",
            color = TextSecondary, fontSize = 12.sp
        )

        // UX fix: Added empty state so users understand the screen is waiting for a
        // draft session, rather than appearing broken (Nielsen #1 — Visibility of System Status).
        val hasSessionData = state.allies.isNotEmpty() || state.enemies.isNotEmpty() ||
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
                        contentDescription = null,
                        tint               = TextDisabled,
                        modifier           = Modifier.size(56.dp)
                    )
                    Text("No active draft session", color = TextSecondary, fontSize = 14.sp)
                    Text(
                        "Start a draft from the Home screen to see live suggestions here.",
                        color    = TextDisabled,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            if (state.allies.isNotEmpty()) {
                Text("YOUR TEAM", color = MLBBTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allies.take(5).forEach { hero ->
                        HeroPortrait(hero = hero, size = 48.dp, showName = true)
                    }
                }
            }

            if (state.enemies.isNotEmpty()) {
                Text("ENEMY TEAM", color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.enemies.take(5).forEach { hero ->
                        HeroPortrait(hero = hero, size = 48.dp, showName = true)
                    }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                Text("TOP SUGGESTIONS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.suggestions.take(5)) { (hero, score) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(SurfaceCard, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroPortrait(hero = hero, size = 40.dp)
                            Column {
                                Text(hero.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Score: %.0f%%".format(score * 100), color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
