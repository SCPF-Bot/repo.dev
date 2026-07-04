package com.mlbb.assistant.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.MLBBBlue
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary

@Composable
fun HomeScreen(
    onStartDraft:   () -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenMeta:     () -> Unit,
    onOpenHistory:  () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(SurfaceDark)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top app bar with gradient ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E),
                                SurfaceMid
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MLBBGold.copy(alpha = 0.15f))
                            .border(1.dp, MLBBGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.SportsMartialArts,
                            contentDescription = null,
                            tint     = MLBBGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            "MLBB ASSISTANT",
                            color      = MLBBGold,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Draft smarter. Win more.",
                            color    = TextSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }

            // ── Gold accent line ─────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MLBBGold.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Meta banner
                MetaBanner(onViewMeta = onOpenMeta)

                // Section 5.1.2 — Your Insights card
                InsightsCard(insights = uiState.insights)

                // Quick actions
                SectionHeader("QUICK ACTIONS")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard("Hero Explorer",  Icons.Rounded.Person,      MLBBBlue,      Modifier.weight(1f)) { onOpenExplorer() }
                    QuickActionCard("Meta Board",     Icons.Rounded.Leaderboard, MLBBTeal,      Modifier.weight(1f)) { onOpenMeta() }
                    QuickActionCard("Draft History",  Icons.Rounded.History,     TextSecondary, Modifier.weight(1f)) { onOpenHistory() }
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
                        CircularProgressIndicator(color = MLBBGold, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        ExtendedFloatingActionButton(
            onClick          = onStartDraft,
            modifier         = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            icon             = { Icon(Icons.Rounded.SportsKabaddi, contentDescription = null) },
            text             = { Text("Start Draft", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
            containerColor   = MLBBGold,
            contentColor     = SurfaceDark
        )
    }
}

// ── Section 5.1.2 — Your Insights card ───────────────────────────────────────

/**
 * Shows aggregated insights from the user's draft history once they have
 * at least [InsightsState.MIN_FOR_INSIGHTS] real sessions with outcomes.
 */
@Composable
private fun InsightsCard(insights: InsightsState) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Your insights" },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, MLBBGold.copy(alpha = 0.25f)),
        shape  = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // Gold accent strip at top
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(MLBBGold.copy(alpha = 0.8f), MLBBGold.copy(alpha = 0.0f))
                    )
                )
        )

        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.insights_title),
                    color = MLBBGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge
                )
            }

            if (!insights.isAvailable) {
                val needed = insights.sessionsNeeded
                Text(
                    stringResource(R.string.insights_need_more, needed),
                    color    = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InsightStat(
                        label = "Win Rate",
                        value = "${insights.winRatePct}%",
                        color = if (insights.winRatePct >= 50) SuccessGreen else MLBBGold
                    )
                    // Thin divider
                    Box(Modifier.width(1.dp).height(40.dp).background(SurfaceElevated).align(Alignment.CenterVertically))
                    InsightStat(
                        label = "Sessions",
                        value = "${insights.sessionCount}",
                        color = MLBBGold
                    )
                    Box(Modifier.width(1.dp).height(40.dp).background(SurfaceElevated).align(Alignment.CenterVertically))
                    InsightStat(
                        label = "Followed Recs",
                        value = "${insights.recommendationFollowPct}%",
                        color = MLBBTeal
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "$label: $value" }
    ) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MetaBanner(onViewMeta: () -> Unit) {
    androidx.compose.material3.Card(
        onClick    = onViewMeta,
        modifier   = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "View current meta tier list" },
        colors     = CardDefaults.cardColors(containerColor = Color.Transparent),
        border     = androidx.compose.foundation.BorderStroke(1.dp, MLBBGold.copy(alpha = 0.35f)),
        shape      = RoundedCornerShape(14.dp),
        elevation  = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MLBBGold.copy(alpha = 0.12f),
                            SurfaceCard
                        )
                    )
                )
        ) {
            Row(
                modifier  = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MLBBGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint     = MLBBGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("CURRENT META", color = MLBBGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text("Tap to see full tier list", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MLBBGold.copy(alpha = 0.7f))
            }
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
    androidx.compose.material3.Card(
        onClick   = onClick,
        // wrapContentHeight: let the card grow to fit icon + label — the old
        // aspectRatio(1.5f) produced ~69 dp of height which clipped the label text.
        modifier  = modifier
            .wrapContentHeight()
            .semantics { contentDescription = label },
        colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
        border    = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        ) {
            Column(
                // fillMaxSize removed: card is now intrinsic-height, so fillMaxSize
                // would collapse to zero before content is measured.
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Text(
                    label,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.labelMedium,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetaHeroCard(hero: Hero) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceElevated, RoundedCornerShape(12.dp))
            .padding(8.dp)
            .width(76.dp)
    ) {
        HeroPortrait(hero = hero, size = 52.dp, showTier = true)
        Spacer(Modifier.height(5.dp))
        Text(
            hero.name,
            color      = TextPrimary,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        Text(
            "%.0f%% win".format(hero.winRate * 100),
            color    = TextSecondary,
            style    = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MLBBGold)
        )
        Text(
            title,
            color      = TextSecondary,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
