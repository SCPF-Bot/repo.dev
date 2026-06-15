package com.mlbb.assistant.presentation.herodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*

enum class DetailTab { OVERVIEW, COUNTERS, SYNERGIES }

@Composable
fun HeroDetailScreen(
    hero: Hero,
    relatedHeroes: Map<Int, Hero>,
    onBack: () -> Unit,
    onAddToEnemy: ((Hero) -> Unit)? = null,
    onAddToYours: ((Hero) -> Unit)? = null
) {
    var activeTab by remember { mutableStateOf(DetailTab.OVERVIEW) }

    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        // Hero banner
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            AsyncImage(
                model = hero.imageUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color.Transparent, SurfaceDark)
                )
            ))
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(hero.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(buildString {
                        append(hero.role)
                        hero.secondaryRole?.let { append(" / $it") }
                        append("  •  ${hero.tier.display} Tier")
                    }, color = tierColor(hero.tier), fontSize = 12.sp)
                }
                Text("← Back", color = MLBBGold, fontSize = 12.sp,
                    modifier = Modifier.clickableNoRipple { onBack() })
            }
        }

        // Stats row
        StatRow(hero = hero)

        // Tabs
        Row(
            Modifier.fillMaxWidth().background(SurfaceMid),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (selected) MLBBGold.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (selected) Modifier.bottomBorder(2.dp, MLBBGold) else Modifier)
                        .padding(vertical = 10.dp)
                        .clickableNoRipple { activeTab = tab },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected) MLBBGold else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Tab content
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                DetailTab.OVERVIEW -> OverviewTab(hero = hero)
                DetailTab.COUNTERS -> HeroRelationTab(
                    sectionA = "Heroes that BEAT ${hero.name}",
                    idsA     = hero.counteredBy,
                    sectionB = "${hero.name} BEATS",
                    idsB     = hero.counters,
                    related  = relatedHeroes
                )
                DetailTab.SYNERGIES -> HeroRelationTab(
                    sectionA = "BEST PARTNERS",
                    idsA     = hero.synergies,
                    sectionB = "WORKS AGAINST",
                    idsB     = hero.counters,
                    related  = relatedHeroes
                )
            }
        }

        // Action buttons (used from overlay context)
        if (onAddToEnemy != null || onAddToYours != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                onAddToEnemy?.let { fn ->
                    ActionBtn("Add to Enemy", ErrorRed, Modifier.weight(1f)) { fn(hero) }
                }
                onAddToYours?.let { fn ->
                    ActionBtn("Add to Your Team", MLBBTeal, Modifier.weight(1f)) { fn(hero) }
                }
            }
        }
    }
}

@Composable
private fun StatRow(hero: Hero) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceCard).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("Win Rate",  "%.0f%%".format(hero.winRate  * 100), SuccessGreen)
        StatItem("Pick Rate", "%.0f%%".format(hero.pickRate * 100), InfoBlue)
        StatItem("Ban Rate",  "%.0f%%".format(hero.banRate  * 100), MLBBRed)
        StatItem("Trend",
            if (hero.patchTrend >= 0) "+%.0f%%".format(hero.patchTrend * 100)
            else "%.0f%%".format(hero.patchTrend * 100),
            if (hero.patchTrend >= 0) SuccessGreen else ErrorRed)
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = TextDisabled, fontSize = 9.sp)
    }
}

@Composable
private fun OverviewTab(hero: Hero) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoRow("Lane",   hero.lane.display)
        InfoRow("Role",   buildString { append(hero.role); hero.secondaryRole?.let { append(" / $it") } })
        InfoRow("Tier",   hero.tier.display)
        if (hero.isOP)             InfoTag("OP PICK",     MLBBGold)
        if (hero.isToxicMechanic)  InfoTag("TOXIC MECHANIC", MLBBRed)
        if (hero.flexLanes.isNotEmpty()) {
            InfoRow("Flex Lanes", hero.flexLanes.joinToString(", ") { it.display })
        }
        SectionTitle("RECOMMENDED SPELLS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hero.recommendedSpells.forEach { spell ->
                Chip(spell, MLBBGold)
            }
        }
        SectionTitle("CORE BUILD ORDER")
        hero.coreItems.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).background(MLBBGold.copy(0.15f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("${item.priority}", color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Text(item.name, color = TextPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HeroRelationTab(
    sectionA: String, idsA: List<Int>,
    sectionB: String, idsB: List<Int>,
    related: Map<Int, Hero>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(sectionA)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            idsA.take(5).mapNotNull { related[it] }.forEach { h ->
                HeroPortrait(hero = h, size = 48.dp, showName = true)
            }
        }
        SectionTitle(sectionB)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            idsB.take(5).mapNotNull { related[it] }.forEach { h ->
                HeroPortrait(hero = h, size = 48.dp, showName = true)
            }
        }
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value,  color = TextPrimary,  fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoTag(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(0.40f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Chip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .background(color.copy(0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, color = color, fontSize = 11.sp) }
}

@Composable
private fun ActionBtn(label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(color.copy(0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(0.40f), RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp)
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

private fun tierColor(tier: com.mlbb.assistant.domain.model.Tier) = when (tier) {
    com.mlbb.assistant.domain.model.Tier.S_PLUS -> TierSPlus
    com.mlbb.assistant.domain.model.Tier.S      -> TierS
    com.mlbb.assistant.domain.model.Tier.A_PLUS -> TierAPlus
    else -> TierA
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(
        androidx.compose.foundation.clickable(
            indication        = null,
            interactionSource = interactionSource,
            onClick           = onClick
        )
    )
}

@Composable
private fun Modifier.bottomBorder(width: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color): Modifier {
    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { width.toPx() }
    return this.drawBehind {
        drawLine(
            color       = color,
            start       = androidx.compose.ui.geometry.Offset(0f, size.height),
            end         = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = widthPx
        )
    }
}
