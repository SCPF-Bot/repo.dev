package com.mlbb.assistant.presentation.metaboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * Number of shimmer placeholder rows shown below each tier label
 * while the hero list loads.
 */
private const val SHIMMER_ROW_COUNT    = 5
private const val SHIMMER_HERO_PER_ROW = 5
private const val SHIMMER_PORTRAIT_DP  = 48

@Composable
fun MetaBoardScreen(
    heroes: List<Hero>,
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit,
    /**
     * When true, the tab content is replaced by a shimmer skeleton.
     * Defaults to false so existing call sites without loading state
     * continue to behave as before.
     */
    isLoading: Boolean = false,
    /**
     * Semantic patch label from [MetaSnapshotDto.patchVersion], e.g. "1.9.14".
     * Displayed next to the META BOARD heading when non-null (todo §8).
     */
    patchVersion: String? = null,
    /**
     * ISO-8601 timestamp from [MetaSnapshotDto.lastUpdated].
     * Shown as a sub-caption below the patch label when non-null (todo §8).
     */
    lastUpdated: String? = null
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
                        color      = MLBBGold,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                    if (patchVersion != null) {
                        Text(
                            "v$patchVersion",
                            color    = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (lastUpdated != null) {
                    Text(
                        "Updated: $lastUpdated",
                        color    = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.38f),
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(Modifier.size(48.dp))
        }

        // M3 PrimaryTabRow — fully accessible, handles selected state announcement
        PrimaryTabRow(
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

/**
 * Empty state shown when the hero list is loaded but contains no entries
 * (e.g. network sync failed and DB is empty for a fresh install).
 *
 * todo §7: Overlay + MetaBoard empty/error states.
 */
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
                modifier = Modifier.size(48.dp)
            )
            Text("No meta data available", color = TextSecondary, fontSize = 14.sp)
            Text(
                "Sync failed or still loading — check your connection and pull to refresh.",
                color    = TextDisabled,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Shimmer skeleton that mimics the tier-list layout while heroes are loading.
 *
 * Renders [SHIMMER_ROW_COUNT] synthetic tier rows, each containing
 * [SHIMMER_HERO_PER_ROW] portrait-sized shimmer boxes. All shimmer boxes
 * share a single [com.valentinilk.shimmer.ShimmerInstance] applied to
 * the parent column so they pulse synchronously.
 */
@Composable
private fun MetaBoardLoadingSkeleton() {
    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(SHIMMER_ROW_COUNT) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top
            ) {
                // Tier label placeholder
                Box(
                    Modifier
                        .width(32.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceMid)
                )
                // Hero portrait placeholders
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(SHIMMER_HERO_PER_ROW) {
                        Box(
                            Modifier
                                .size(SHIMMER_PORTRAIT_DP.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                        Icons.AutoMirrored.Rounded.TrendingUp,
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
    Tier.S_PLUS  -> TierSPlus
    Tier.S       -> TierS
    Tier.A_PLUS  -> TierAPlus
    Tier.A       -> TierA
    Tier.B       -> TierB
    Tier.UNKNOWN -> TierB    // fallback — display at lowest tier colour
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
