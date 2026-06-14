package com.mlbb.assistant.presentation.metaboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*

enum class MetaTab { TIER_LIST, TRENDING, BY_ROLE }

@Composable
fun MetaBoardScreen(heroes: List<Hero>, onHeroClick: (Hero) -> Unit, onBack: () -> Unit) {
    var activeTab by remember { mutableStateOf(MetaTab.TIER_LIST) }

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(SurfaceMid).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📊 META BOARD", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("← Back", color = MLBBGold, fontSize = 12.sp, modifier = Modifier.clickable { onBack() })
        }

        // Tabs
        Row(Modifier.fillMaxWidth().background(SurfaceMid), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetaTab.entries.forEach { tab ->
                val sel = activeTab == tab
                Box(
                    Modifier.weight(1f)
                        .background(if (sel) MLBBGold.copy(0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { activeTab = tab }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(when (tab) {
                        MetaTab.TIER_LIST -> "Tier List"
                        MetaTab.TRENDING  -> "Trending"
                        MetaTab.BY_ROLE   -> "By Role"
                    }, color = if (sel) MLBBGold else TextSecondary, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
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
    val grouped = Tier.entries.associateWith { tier -> heroes.filter { it.tier == tier } }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tier.entries.forEach { tier ->
            val hs = grouped[tier] ?: emptyList()
            if (hs.isNotEmpty()) {
                item {
                    TierRow(tier = tier, heroes = hs, onHeroClick = onHeroClick)
                }
            }
        }
    }
}

@Composable
private fun TierRow(tier: Tier, heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val color = tierColor(tier)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.width(32.dp).background(color.copy(0.20f), RoundedCornerShape(6.dp)).padding(vertical = 8.dp),
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
    val sorted = heroes.sortedByDescending { it.patchTrend }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sorted.take(20)) { hero ->
            TrendingRow(hero = hero, onHeroClick = onHeroClick)
        }
    }
}

@Composable
private fun TrendingRow(hero: Hero, onHeroClick: (Hero) -> Unit) {
    val isRising = hero.patchTrend >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(8.dp))
            .clickable { onHeroClick(hero) }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroPortrait(hero = hero, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(hero.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${hero.role}  •  ${hero.tier.display}", color = TextSecondary, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (isRising) "↑ +%.0f%%".format(hero.patchTrend * 100)
                else "↓ %.0f%%".format(hero.patchTrend * 100),
                color = if (isRising) SuccessGreen else ErrorRed,
                fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
            Text("win rate trend", color = TextDisabled, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ByRoleView(heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val roles = listOf("Tank","Fighter","Mage","Marksman","Support","Assassin")
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(roles) { role ->
            val roleHeroes = heroes.filter { it.role == role }.sortedByDescending { it.winRate }
            if (roleHeroes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(role.uppercase(), color = roleColor(role), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(roleHeroes.take(8)) { hero ->
                            HeroPortrait(hero = hero, size = 52.dp, showName = true, showTier = true, onClick = onHeroClick)
                        }
                    }
                }
            }
        }
    }
}

private fun tierColor(tier: Tier) = when (tier) {
    Tier.S_PLUS -> TierSPlus; Tier.S -> TierS; Tier.A_PLUS -> TierAPlus; Tier.A -> TierA; Tier.B -> TierB
}
private fun roleColor(role: String) = when (role) {
    "Tank" -> RoleColorTank; "Fighter" -> RoleColorFighter; "Mage" -> RoleColorMage
    "Marksman" -> RoleColorMarksman; "Support" -> RoleColorSupport; "Assassin" -> RoleColorAssassin
    else -> TextSecondary
}
