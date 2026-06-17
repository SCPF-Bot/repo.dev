package com.mlbb.assistant.presentation.metaboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*

enum class MetaTab { TIER_LIST, TRENDING, BY_ROLE }

@Composable
fun MetaBoardScreen(
    heroes: List<Hero>,
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf(MetaTab.TIER_LIST) }

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Header — M3-aligned, proper back button
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BackButton(onBack = onBack)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Leaderboard, contentDescription = null,
                    tint = MLBBGold, modifier = Modifier.size(18.dp))
                Text("META BOARD", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.size(48.dp))
        }

        // M3 TabRow — fully accessible, handles selected state announcement
        TabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor   = SurfaceMid,
            contentColor     = MLBBGold
        ) {
            MetaTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick  = { activeTab = tab },
                    text = {
                        Text(when (tab) {
                            MetaTab.TIER_LIST -> "Tier List"
                            MetaTab.TRENDING  -> "Trending"
                            MetaTab.BY_ROLE   -> "By Role"
                        })
                    }
                )
            }
        }

        when (activeTab) {
            MetaTab.TIER_LIST -> TierListView(heroes = heroes, onHeroClick = onHeroClick)
            MetaTab.TRENDING  -> TrendingView(heroes = heroes, onHeroClick = onHeroClick)
            MetaTab.BY_ROLE   -> ByRoleView(heroes = heroes, onHeroClick = onHeroClick)
        }
    }
}

@Composable
private fun TierListView(heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val tieredHeroes = remember(heroes) {
        Tier.entries.mapNotNull { tier ->
            val hs = heroes.filter { it.tier == tier }
            if (hs.isNotEmpty()) tier to hs else null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tieredHeroes, key = { it.first.name }) { (tier, hs) ->
            TierRow(tier = tier, heroes = hs, onHeroClick = onHeroClick)
        }
    }
}

@Composable
private fun TierRow(tier: Tier, heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val color = tierColor(tier)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            Modifier
                .width(32.dp)
                .background(color.copy(0.20f), RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text(tier.display, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp) }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(heroes) { hero ->
                HeroPortrait(hero = hero, size = 48.dp, showName = true, onClick = onHeroClick)
            }
        }
    }
}

@Composable
private fun TrendingView(heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val sorted = remember(heroes) { heroes.sortedByDescending { it.patchTrend } }
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sorted.take(20), key = { it.id }) { hero ->
            TrendingRow(hero = hero, onHeroClick = onHeroClick)
        }
    }
}

@Composable
private fun TrendingRow(hero: Hero, onHeroClick: (Hero) -> Unit) {
    val isRising = hero.patchTrend >= 0
    Card(
        onClick = { onHeroClick(hero) },
        colors  = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape   = RoundedCornerShape(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            HeroPortrait(hero = hero, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(hero.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${hero.role}  •  ${hero.tier.display}", color = TextSecondary, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        Icons.Rounded.TrendingUp,
                        contentDescription = if (isRising) "Rising" else "Falling",
                        tint   = if (isRising) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (isRising) "+%.0f%%".format(hero.patchTrend * 100)
                        else "%.0f%%".format(hero.patchTrend * 100),
                        color = if (isRising) SuccessGreen else ErrorRed,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                }
                Text("trend", color = TextDisabled, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ByRoleView(heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val roles = listOf("Tank", "Fighter", "Mage", "Marksman", "Support", "Assassin")
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(roles) { role ->
            val roleHeroes = remember(heroes, role) {
                heroes.filter { it.role == role }.sortedByDescending { it.winRate }
            }
            if (roleHeroes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(role.uppercase(), color = roleColor(role),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(roleHeroes.take(8), key = { it.id }) { hero ->
                            HeroPortrait(hero = hero, size = 52.dp, showName = true,
                                showTier = true, onClick = onHeroClick)
                        }
                    }
                }
            }
        }
    }
}

// One branch per line — no semicolon packing
private fun tierColor(tier: Tier) = when (tier) {
    Tier.S_PLUS -> TierSPlus
    Tier.S      -> TierS
    Tier.A_PLUS -> TierAPlus
    Tier.A      -> TierA
    Tier.B      -> TierB
}

private fun roleColor(role: String) = when (role) {
    "Tank"      -> RoleColorTank
    "Fighter"   -> RoleColorFighter
    "Mage"      -> RoleColorMage
    "Marksman"  -> RoleColorMarksman
    "Support"   -> RoleColorSupport
    "Assassin"  -> RoleColorAssassin
    else        -> TextSecondary
}
