package com.mlbb.assistant.presentation.herodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.InfoBlue
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.TierA
import com.mlbb.assistant.presentation.common.theme.TierAPlus
import com.mlbb.assistant.presentation.common.theme.TierB
import com.mlbb.assistant.presentation.common.theme.TierS
import com.mlbb.assistant.presentation.common.theme.TierSPlus

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

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {

        // Hero banner with Coil placeholder/error — ContentScale.Crop
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(hero.imageUrl)
                    .build(),
                contentDescription = "${hero.name} splash art",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            // Gradient overlay
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, SurfaceDark))
                )
            )
            // Back button + hero info
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(4.dp),
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BackButton(onBack = onBack, modifier = Modifier.align(Alignment.CenterVertically))
                Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                    Text(hero.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(buildString {
                        append(hero.role)
                        hero.secondaryRole?.let { append(" / $it") }
                        append("  •  ${hero.tier.display} Tier")
                    }, color = tierColor(hero.tier), fontSize = 12.sp)
                }
            }
        }

        // Stats row
        StatRow(hero = hero)

        // M3 PrimaryTabRow — accessible, handles selection announcements, underline indicator
        PrimaryTabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor   = SurfaceMid,
            contentColor     = MLBBGold
        ) {
            DetailTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick  = { activeTab = tab },
                    text     = {
                        Text(tab.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                )
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
                DetailTab.OVERVIEW   -> OverviewTab(hero = hero)
                DetailTab.COUNTERS   -> HeroRelationTab(
                    sectionA = "Heroes that BEAT ${hero.name}",
                    idsA     = hero.counteredBy,
                    sectionB = "${hero.name} BEATS",
                    idsB     = hero.counters,
                    related  = relatedHeroes
                )
                DetailTab.SYNERGIES  -> HeroRelationTab(
                    sectionA = "BEST PARTNERS",
                    idsA     = hero.synergies,
                    sectionB = "WORKS AGAINST",
                    idsB     = hero.counters,
                    related  = relatedHeroes
                )
            }
        }

        // Action buttons
        if (onAddToEnemy != null || onAddToYours != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceMid)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                onAddToEnemy?.let { fn ->
                    Button(
                        onClick  = { fn(hero) },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) { Text("Add to Enemy") }
                }
                onAddToYours?.let { fn ->
                    Button(
                        onClick  = { fn(hero) },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = MLBBTeal)
                    ) { Text("Add to Yours") }
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
            if (hero.patchTrend >= 0) SuccessGreen else ErrorRed
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        // UX fix: raised from 9.sp to 10.sp — minimum legible text size to meet
        // WCAG readability guidance (small text ≥ 10sp on mobile displays).
        Text(label, color = TextDisabled, fontSize = 10.sp)
    }
}

@Composable
private fun OverviewTab(hero: Hero) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoRow("Lane",  hero.lane.display)
        InfoRow("Role",  buildString { append(hero.role); hero.secondaryRole?.let { append(" / $it") } })
        InfoRow("Tier",  hero.tier.display)
        if (hero.isOP)            InfoTag("OP PICK",        MLBBGold)
        if (hero.isToxicMechanic) InfoTag("TOXIC MECHANIC", MLBBRed)
        if (hero.flexLanes.isNotEmpty()) {
            InfoRow("Flex Lanes", hero.flexLanes.joinToString(", ") { it.display })
        }
        SectionTitle("RECOMMENDED SPELLS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hero.recommendedSpells.forEach { Chip(it, MLBBGold) }
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
        if (idsA.isEmpty()) Text("No data", color = TextDisabled, fontSize = 12.sp)
        // Pass 4 UX fix: renamed private Divider → ThinDivider to avoid shadowing
        // M3's HorizontalDivider composable — shadowing causes silent import ambiguity
        // and breaks future callers that expect the M3 component (Pass 4 import check).
        ThinDivider()
        SectionTitle(sectionB)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            idsB.take(5).mapNotNull { related[it] }.forEach { h ->
                HeroPortrait(hero = h, size = 48.dp, showName = true)
            }
        }
        if (idsB.isEmpty()) Text("No data", color = TextDisabled, fontSize = 12.sp)
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)

// Pass 4 fix: renamed from Divider → ThinDivider to avoid shadowing M3's HorizontalDivider.
@Composable
private fun ThinDivider() =
    Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceElevated))

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value,  color = TextPrimary,  fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoTag(label: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(0.40f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Chip(label: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(label, color = color, fontSize = 11.sp) }
}

// Exhaustive on sealed enum — compiler enforces all cases, no silent else fallback
private fun tierColor(tier: Tier): Color = when (tier) {
    Tier.S_PLUS  -> TierSPlus
    Tier.S       -> TierS
    Tier.A_PLUS  -> TierAPlus
    Tier.A       -> TierA
    Tier.B       -> TierB
    Tier.UNKNOWN -> TierB    // fallback — display at lowest tier colour
}
