package com.mlbb.assistant.presentation.herolist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.components.HeroGrid
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun HeroListScreen(
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Header — proper back button with 48dp touch target
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BackButton(onBack = onBack)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null,
                    tint = MLBBGold, modifier = Modifier.size(18.dp))
                Text("HERO EXPLORER", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            // Balance the back button width
            Spacer(Modifier.size(48.dp))
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MLBBGold)
            }
            state.heroes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Person, contentDescription = null,
                        tint = TextDisabled, modifier = Modifier.size(48.dp))
                    Text("No heroes loaded", color = TextSecondary)
                    Text("Pull to refresh or check connection", color = TextDisabled, fontSize = 12.sp)
                }
            }
            else -> HeroGrid(
                heroes      = state.filteredHeroes.ifEmpty { state.heroes },
                disabledIds = emptySet(),
                onHeroTap   = { hero -> onHeroClick(hero) },
                modifier    = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
        }
    }
}
