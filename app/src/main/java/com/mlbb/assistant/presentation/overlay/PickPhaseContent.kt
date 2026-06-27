package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.engine.DraftSession
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.scoring.HeroScore
import com.mlbb.assistant.presentation.common.components.HeroGrid
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.components.RoleDashboard
import com.mlbb.assistant.presentation.common.theme.*
import kotlinx.coroutines.delay
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.compose.Balloon
import com.skydoves.balloon.compose.rememberBalloonBuilder

enum class SuggestionTab { META, SYNERGY, COUNTER }

@Composable
fun PickPhaseContent(
    session: DraftSession,
    recommendations: List<HeroScore>,
    allHeroes: List<Hero>,
    enemyWarnings: List<String>,
    isOurTurn: Boolean,
    pickLabel: String,
    onHeroTap: (Hero) -> Unit,
    onHeroLongPress: (Hero) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(SuggestionTab.META) }

    val tabFiltered = remember(recommendations, activeTab) {
        when (activeTab) {
            SuggestionTab.META    -> recommendations.sortedByDescending { it.metaScore }
            SuggestionTab.SYNERGY -> recommendations.sortedByDescending { it.synergyScore }
            SuggestionTab.COUNTER -> recommendations.sortedByDescending { it.counterScore }
        }.take(6)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBackground, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️ PICK PHASE  $pickLabel", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (isOurTurn) {
                Text("YOUR TURN", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            } else {
                Text("ENEMY TURN", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }

        // Pick slots
        PickSlotRows(session = session)

        // Role dashboard
        RoleDashboard(picks = session.ourPickedHeroes, modifier = Modifier.fillMaxWidth())

        // Enemy comp warnings
        if (enemyWarnings.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarningAmber.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                    .border(1.dp, WarningAmber.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                enemyWarnings.take(2).forEach { warning ->
                    Text(warning, color = WarningAmber, fontSize = 10.sp)
                }
            }
        }

        // Suggestion tabs
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SuggestionTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            if (selected) MLBBGold.copy(alpha = 0.20f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .combinedClickable(onClick = { activeTab = tab })
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (tab) {
                            SuggestionTab.META    -> "★ META"
                            SuggestionTab.SYNERGY -> "🔗 SYNERGY"
                            SuggestionTab.COUNTER -> "🛡 COUNTER"
                        },
                        color    = if (selected) MLBBGold else TextSecondary,
                        fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Recommendations — Lottie scanning animation when analysing; cards when ready
        // Pick-success Lottie overlay fires for ≈1.4 s after the player taps a hero (rec. §5.2 / RA-07).
        var lastPickedHero by remember { mutableStateOf<Hero?>(null) }
        lastPickedHero?.let { hero ->
            PickSuccessOverlay(hero = hero, onDone = { lastPickedHero = null })
        }

        if (tabFiltered.isEmpty()) {
            ScanningPlaceholder()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabFiltered.take(3).forEach { score ->
                    RecommendationCard(
                        score  = score,
                        onTap  = { hero -> lastPickedHero = hero; onHeroTap(hero) },
                        onLong = onHeroLongPress
                    )
                }
            }
        }

        // Hero grid
        HeroGrid(
            heroes          = allHeroes,
            disabledIds     = session.unavailableIds,
            onHeroTap       = onHeroTap,
            onHeroLongPress = onHeroLongPress,
            modifier        = Modifier.heightIn(max = 180.dp)
        )
    }
}

/**
 * Lottie scanning placeholder shown while the draft engine analyses the current
 * state and no recommendations are ready yet (rec. §8.4 / RA-07).
 *
 * The spinning dashed-ring animation ([R.raw.lottie_scanning]) communicates that
 * work is in progress — replacing the jarring blank space that appeared before.
 */
@Composable
private fun ScanningPlaceholder() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_scanning))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever
    )
    Box(
        modifier         = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LottieAnimation(
                composition = composition,
                progress    = { progress },
                modifier    = Modifier.size(44.dp)
            )
            Text(
                text     = "Analysing draft…",
                color    = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PickSlotRows(session: DraftSession) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ENEMY TEAM", color = ErrorRed, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            session.enemyPicks.forEach { hero -> HeroPortrait(hero = hero, size = 40.dp) }
        }
        Text("YOUR TEAM", color = MLBBTeal, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            session.ourPicks.forEach { hero -> HeroPortrait(hero = hero, size = 40.dp) }
        }
    }
}

/**
 * Recommendation chip with a Balloon tooltip on long-press (rec. §8.4 / RA-06).
 *
 * Long-pressing any card shows a Balloon popup with the hero's score breakdown:
 * meta score, synergy score, and counter score as percentages.  This gives advanced
 * players the detail they need without cluttering the compact overlay UI.
 */
@Composable
private fun RecommendationCard(score: HeroScore, onTap: (Hero) -> Unit, onLong: (Hero) -> Unit) {
    val balloonBuilder = rememberBalloonBuilder {
        setArrowSize(10)
        setWidth(BalloonSizeSpec.WRAP)
        setHeight(BalloonSizeSpec.WRAP)
        setArrowPosition(0.5f)
        setCornerRadius(10f)
        setPaddingHorizontal(12)
        setPaddingVertical(8)
        setBalloonAnimation(BalloonAnimation.ELASTIC)
        setBackgroundColor(android.graphics.Color.parseColor("#1A1C2E"))
    }

    Balloon(
        builder        = balloonBuilder,
        balloonContent = {
            RecommendationTooltipContent(score = score)
        }
    ) { balloonWindow ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(SurfaceCard, RoundedCornerShape(8.dp))
                .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(6.dp)
                .width(80.dp)
                .combinedClickable(
                    onClick      = { onTap(score.hero) },
                    onLongClick  = { balloonWindow.showAlignBottom() },
                    onLongClickLabel = "View ${score.hero.name} score breakdown"
                )
        ) {
            HeroPortrait(hero = score.hero, size = 56.dp, onClick = onTap)
            Spacer(Modifier.height(3.dp))
            Text(score.hero.name, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Box(
                Modifier
                    .background(badgeColor(score.badgeLabel).copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(score.badgeLabel, color = badgeColor(score.badgeLabel), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Text("%.0f pts".format(score.totalScore * 100), color = TextSecondary, fontSize = 8.sp)
            Text(
                score.reason, color = TextDisabled, fontSize = 7.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecommendationTooltipContent(score: HeroScore) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier            = Modifier.padding(4.dp)
    ) {
        Text(
            text       = score.hero.name,
            color      = MLBBGold,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
        TooltipRow("Meta",    score.metaScore)
        TooltipRow("Synergy", score.synergyScore)
        TooltipRow("Counter", score.counterScore)
        Text(
            text     = score.reason,
            color    = TextDisabled,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TooltipRow(label: String, value: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("$label:", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.width(50.dp))
        Text("%.0f%%".format(value * 100), color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Full-width celebration overlay that plays [R.raw.lottie_pick_success] exactly once
 * (at 1.2× speed, ≈1.4 s total) then calls [onDone] to dismiss itself.
 *
 * Positioned immediately above the recommendation row so the player gets instant
 * visual confirmation that their tap was registered — critical during the 30-second
 * pick clock. The animation completes in the background even if the overlay composable
 * recomposes, because [LaunchedEffect] is keyed to [hero].
 *
 * Acceptance: §5.2 step 3 — pick-success animation plays in the overlay.
 */
@Composable
private fun PickSuccessOverlay(hero: Hero, onDone: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_pick_success))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = 1,
        speed       = 1.2f
    )
    LaunchedEffect(hero) {
        delay(1_400L)
        onDone()
    }
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LottieAnimation(
                composition = composition,
                progress    = { progress },
                modifier    = Modifier.size(52.dp)
            )
            Text(
                text       = "${hero.name} selected!",
                color      = SuccessGreen,
                fontSize   = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun badgeColor(badge: String) = when {
    badge.contains("META")    -> MLBBGold
    badge.contains("SYNERGY") -> MLBBTeal
    badge.contains("COUNTER") -> InfoBlue
    else                       -> TextSecondary
}
