package com.mlbb.assistant.presentation.metaboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*
import com.valentinilk.shimmer.shimmer

enum class MetaTab { TIER_LIST, TRENDING, BY_ROLE }

private const val SHIMMER_ROW_COUNT    = 5
private const val SHIMMER_HERO_PER_ROW = 5
private const val SHIMMER_PORTRAIT_DP  = 52

@Composable
fun MetaBoardScreen(
    heroes: List<Hero>,
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    patchVersion: String? = null,
    lastUpdated: String? = null
) {
    var activeTab by remember { mutableStateOf(MetaTab.TIER_LIST) }

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // ── Header with gradient ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1A2E), SurfaceMid))
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                BackButton(onBack = onBack)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Leaderboard,
                            contentDescription = null,
                            tint     = MLBBGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "META BOARD",
                            color         = MLBBGold,
                            fontWeight    = FontWeight.Bold,
                            fontSize      = 16.sp,
                            letterSpacing = 1.sp
                        )
                        if (patchVersion != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MLBBGold.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "v$patchVersion",
                                    color    = MLBBGold.copy(alpha = 0.80f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (lastUpdated != null) {
                        Text(
                            "Updated: $lastUpdated",
                            color    = TextDisabled,
                            fontSize = 9.sp
                        )
                    }
                }
                Spacer(Modifier.size(48.dp))
            }
        }

        // ── Gold accent line ──────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, MLBBGold.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // ── Tab row ───────────────────────────────────────────────────────────
        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor   = SurfaceMid,
            contentColor     = MLBBGold
        ) {
            MetaTab.entries.forEachIndexed { _, tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick  = { activeTab = tab },
                    text = {
                        Text(
                            when (tab) {
                                MetaTab.TIER_LIST -> "Tier List"
                                MetaTab.TRENDING  -> "Trending"
                                MetaTab.BY_ROLE   -> "By Role"
                            },
                            fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when {
            isLoading        -> MetaBoardLoadingSkeleton()
            heroes.isEmpty() -> MetaBoardEmptyState()
            else -> when (activeTab) {
                MetaTab.TIER_LIST -> TierListView(heroes = heroes, onHeroClick = onHeroClick)
                MetaTab.TRENDING  -> TrendingView(heroes = heroes, onHeroClick = onHeroClick)
                MetaTab.BY_ROLE   -> ByRoleView(heroes = heroes, onHeroClick = onHeroClick)
            }
        }
    }
}

@Composable
private fun MetaBoardEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.Leaderboard,
                contentDescription = null,
                tint     = TextDisabled,
                modifier = Modifier.size(52.dp)
            )
            Text("No meta data available", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Sync failed or still loading — check your connection.",
                color    = TextDisabled,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MetaBoardLoadingSkeleton() {
    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(SHIMMER_ROW_COUNT) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(SHIMMER_PORTRAIT_DP.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceMid)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(SHIMMER_HERO_PER_ROW) {
                        Box(
                            Modifier
                                .size(SHIMMER_PORTRAIT_DP.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceMid)
                        )
                    }
                }
            }
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top
    ) {
        // Tier badge
        Box(
            Modifier
                .width(36.dp)
                .defaultMinSize(minHeight = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.18f))
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                tier.display,
                color      = color,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 14.sp
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(heroes) { hero ->
                HeroPortrait(hero = hero, size = 52.dp, showName = true, onClick = onHeroClick)
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
    val trendColor = if (isRising) SuccessGreen else ErrorRed
    Card(
        onClick = { onHeroClick(hero) },
        colors  = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape   = RoundedCornerShape(12.dp),
        border  = CardDefaults.outlinedCardBorder().copy(width = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            HeroPortrait(hero = hero, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hero.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${hero.role}  •  ${hero.tier.display}", color = TextSecondary, fontSize = 10.sp)
            }
            // Trend indicator
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(trendColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        if (isRising) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                        contentDescription = if (isRising) "Rising" else "Falling",
                        tint   = trendColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (isRising) "+%.0f%%".format(hero.patchTrend * 100)
                        else "%.0f%%".format(hero.patchTrend * 100),
                        color      = trendColor,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ByRoleView(heroes: List<Hero>, onHeroClick: (Hero) -> Unit) {
    val roles = listOf("Tank", "Fighter", "Mage", "Marksman", "Support", "Assassin")
    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(roles) { role ->
            val roleHeroes = remember(heroes, role) {
                heroes.filter { it.role == role }.sortedByDescending { it.winRate }
            }
            if (roleHeroes.isNotEmpty()) {
                val color = roleColor(role)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                        )
                        Text(
                            role.uppercase(),
                            color         = color,
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
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

private fun tierColor(tier: Tier) = when (tier) {
    Tier.S_PLUS  -> TierSPlus
    Tier.S       -> TierS
    Tier.A_PLUS  -> TierAPlus
    Tier.A       -> TierA
    Tier.B       -> TierB
    Tier.UNKNOWN -> TierB
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
