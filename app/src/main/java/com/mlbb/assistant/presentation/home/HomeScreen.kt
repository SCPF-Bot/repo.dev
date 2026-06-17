package com.mlbb.assistant.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun HomeScreen(
    onStartDraft:   () -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenMeta:     () -> Unit,
    onOpenHistory:  () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize().background(SurfaceDark)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SportsMartialArts, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(22.dp))
                    Text("MLBB ASSISTANT", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(
                    onClick  = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = "Settings" }
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = TextSecondary)
                }
            }

            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Meta banner
                MetaBanner(onViewMeta = onOpenMeta)

                // Quick actions
                SectionHeader("QUICK ACTIONS")
                val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val useTwoColumns = screenWidthDp < 600
                if (useTwoColumns) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionCard("Hero Explorer", Icons.Rounded.Person,      MLBBBlue,     Modifier.weight(1f)) { onOpenExplorer() }
                            QuickActionCard("Meta Board",   Icons.Rounded.Leaderboard, MLBBTeal,     Modifier.weight(1f)) { onOpenMeta() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickActionCard("Draft History", Icons.Rounded.History,    TextSecondary, Modifier.weight(1f)) { onOpenHistory() }
                            QuickActionCard("Settings",      Icons.Rounded.Settings,   SurfaceElevated, Modifier.weight(1f)) { onOpenSettings() }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickActionCard("Heroes",  Icons.Rounded.Person,      MLBBBlue,     Modifier.weight(1f)) { onOpenExplorer() }
                        QuickActionCard("Meta",    Icons.Rounded.Leaderboard, MLBBTeal,     Modifier.weight(1f)) { onOpenMeta() }
                        QuickActionCard("History", Icons.Rounded.History,     TextSecondary, Modifier.weight(1f)) { onOpenHistory() }
                        QuickActionCard("Settings",Icons.Rounded.Settings,    SurfaceElevated, Modifier.weight(1f)) { onOpenSettings() }
                    }
                }

                // Top meta heroes
                if (!uiState.isLoading && uiState.topMetaHeroes.isNotEmpty()) {
                    SectionHeader("TOP META HEROES")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.topMetaHeroes) { hero ->
                            MetaHeroCard(hero = hero)
                        }
                    }
                } else if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MLBBGold, modifier = Modifier.size(28.dp))
                    }
                }

                // Bottom spacer so FAB doesn't obscure last card
                Spacer(Modifier.height(80.dp))
            }
        }

        // Start Draft FAB positioned bottom-end
        ExtendedFloatingActionButton(
            onClick          = onStartDraft,
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            icon             = { Icon(Icons.Rounded.SportsKabaddi, contentDescription = null) },
            text             = { Text("Start Draft") },
            containerColor   = MLBBGold,
            contentColor     = SurfaceDark
        )
    }
}

@Composable
private fun MetaBanner(onViewMeta: () -> Unit) {
    Card(
        onClick    = onViewMeta,
        modifier   = Modifier.fillMaxWidth().semantics { contentDescription = "View current meta tier list" },
        colors     = CardDefaults.cardColors(containerColor = SurfaceCard),
        border     = androidx.compose.foundation.BorderStroke(1.dp, MLBBGold.copy(alpha = 0.25f)),
        shape      = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier  = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(20.dp))
                Column {
                    Text("CURRENT META", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Tap to see full tier list", color = TextSecondary, fontSize = 11.sp)
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MLBBGold)
        }
    }
}

@Composable
private fun QuickActionCard(
    label:       String,
    icon:        ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier:    Modifier,
    onClick:     () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = modifier
            .aspectRatio(1.5f)
            .semantics { contentDescription = label },
        colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
        border    = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            Text(label, color = accentColor, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge)
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
        Text(hero.name, color = TextPrimary, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("%.0f%% win".format(hero.winRate * 100), color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color    = TextSecondary,
        style    = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}
