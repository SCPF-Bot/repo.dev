package com.mlbb.assistant.presentation.herolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mlbb.assistant.presentation.common.components.HeroGrid
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun HeroListScreen(
    onBack: () -> Unit,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceMid).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🗡️ HERO EXPLORER", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("← Back", color = MLBBGold, fontSize = 12.sp, modifier = Modifier.clickable { onBack() })
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MLBBGold)
            }
            state.heroes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No heroes loaded", color = TextSecondary)
            }
            else -> HeroGrid(
                heroes       = state.filteredHeroes.ifEmpty { state.heroes },
                disabledIds  = emptySet(),
                onHeroTap    = { /* navigate to detail */ },
                modifier     = Modifier.fillMaxSize().padding(10.dp)
            )
        }
    }
}
