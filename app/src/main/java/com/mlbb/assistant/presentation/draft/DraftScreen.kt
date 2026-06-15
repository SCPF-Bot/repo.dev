package com.mlbb.assistant.presentation.draft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun DraftScreen(viewModel: DraftViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    // DraftScreen is now a lightweight fallback/standalone view.
    // The primary draft UI is the overlay (DraftPanel + BanPhaseContent + PickPhaseContent).
    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⚔️ DRAFT PLANNER", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Use the floating overlay during MLBB draft for the full experience. " +
            "This view shows the current session state.",
            color = TextSecondary, fontSize = 12.sp
        )

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
                            .background(SurfaceCard, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
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
