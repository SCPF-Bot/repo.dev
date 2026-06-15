package com.mlbb.assistant.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*
import com.mlbb.assistant.presentation.herolist.HeroListViewModel

@Composable
fun HomeScreen(
    onStartDraft:    () -> Unit,
    onOpenExplorer:  () -> Unit,
    onOpenMeta:      () -> Unit,
    onOpenHistory:   () -> Unit,
    onOpenSettings:  () -> Unit,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val topMeta      = state.heroes.take(5)
    // Placeholder until real history is wired up: stable shuffle keyed to hero-list identity
    val recentlyUsed = remember(state.heroes) { state.heroes.shuffled().take(3) }

    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("⚔️  MLBB ASSISTANT", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚙️", fontSize = 20.sp, modifier = Modifier.clickable { onOpenSettings() })
            }
        }

        // Patch banner
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(12.dp))
                .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("🔥 CURRENT META", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Tap to see full tier list", color = TextSecondary, fontSize = 11.sp)
                }
                Text("View →", color = MLBBGold, fontSize = 12.sp, modifier = Modifier.clickable { onOpenMeta() })
            }
        }

        // Quick actions grid
        Text("QUICK ACTIONS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("⚔️", "Start Draft",    MLBBGold,   Modifier.weight(1f)) { onStartDraft() }
            QuickActionCard("📊", "Meta Board",     MLBBTeal,   Modifier.weight(1f)) { onOpenMeta() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("🗡️", "Hero Explorer", MLBBBlue,   Modifier.weight(1f)) { onOpenExplorer() }
            QuickActionCard("📜", "Draft History", TextSecondary, Modifier.weight(1f)) { onOpenHistory() }
        }

        // Top meta heroes
        if (topMeta.isNotEmpty()) {
            Text("🔥 TOP META HEROES", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(topMeta) { hero ->
                    MetaHeroCard(hero = hero)
                }
            }
        }

        // Recently used
        if (recentlyUsed.isNotEmpty()) {
            Text("YOUR MOST USED HEROES", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                recentlyUsed.forEach { hero ->
                    HeroPortrait(hero = hero, size = 56.dp, showName = true, showTier = true)
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: String, label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier, onClick: () -> Unit
) {
    Box(
        modifier
            .aspectRatio(1.6f)
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, fontSize = 22.sp)
            Text(label, color = accentColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetaHeroCard(hero: Hero) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(SurfaceCard, RoundedCornerShape(10.dp))
            .border(1.dp, SurfaceElevated, RoundedCornerShape(10.dp))
            .padding(8.dp)
            .width(64.dp)
    ) {
        HeroPortrait(hero = hero, size = 52.dp, showTier = true)
        Spacer(Modifier.height(4.dp))
        Text(hero.name, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("%.0f%% win".format(hero.winRate * 100), color = TextSecondary, fontSize = 8.sp)
    }
}
