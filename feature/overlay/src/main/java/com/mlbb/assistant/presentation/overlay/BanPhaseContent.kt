package com.mlbb.assistant.presentation.overlay

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mlbb.assistant.feature.overlay.R
import com.mlbb.assistant.domain.advisor.BanSuggestion
import com.mlbb.assistant.domain.engine.BanStructure
import com.mlbb.assistant.domain.engine.DraftPhase
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.HeroGrid
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun BanPhaseContent(
    phase: DraftPhase,
    banStructure: BanStructure,
    enemyBansR1: List<Hero?>,
    ourBansR1: List<Hero?>,
    enemyBansR2: List<Hero?>,
    ourBansR2: List<Hero?>,
    banSuggestions: List<BanSuggestion>,
    allHeroes: List<Hero>,
    disabledIds: Set<Int>,
    isBanButtonVisible: Boolean,
    onHeroSelected: (Hero) -> Unit,
    onHeroLongPress: (Hero) -> Unit,
    modifier: Modifier = Modifier
) {
    val round = if (phase == DraftPhase.BAN_ROUND_2) 2 else 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBackground, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚔️ BAN PHASE  Round $round of ${if (banStructure.hasRound2) 2 else 1}",
                color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
            Text(
                "${banStructure.totalBans} total bans",
                color = TextSecondary, fontSize = 10.sp
            )
        }

        // "YOUR TURN TO BAN" banner — Lottie warning pulse animation (rec. §8.4 / RA-07)
        if (isBanButtonVisible) {
            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                BanTurnBanner()
            }
        }

        // Enemy ban slots
        BanSlotRow(label = "ENEMY BANS", bans = enemyBansR1, r2Bans = enemyBansR2, banStructure = banStructure)

        // Our ban slots
        BanSlotRow(label = "YOUR TEAM BANS", bans = ourBansR1, r2Bans = ourBansR2, banStructure = banStructure,
            labelColor = MLBBTeal)

        // Recommendations
        if (banSuggestions.isNotEmpty() && isBanButtonVisible) {
            BanSuggestionRow(suggestions = banSuggestions, onTap = onHeroSelected)
        }

        // Hero grid
        HeroGrid(
            heroes        = allHeroes,
            disabledIds   = disabledIds,
            onHeroTap     = onHeroSelected,
            onHeroLongPress = onHeroLongPress,
            modifier      = Modifier.heightIn(max = 200.dp)
        )
    }
}

/**
 * "YOUR TURN TO BAN" banner with a Lottie warning-pulse animation.
 *
 * The animation ([R.raw.lottie_ban_warning]) plays a red pulsing ring on loop to
 * draw the player's attention when it's their turn. Lottie renders it with hardware
 * acceleration so it does not impact the overlay's main-thread compositing budget.
 */
@Composable
private fun BanTurnBanner() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_ban_warning))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = LottieConstants.IterateForever
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MLBBRed.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .border(1.dp, MLBBRed.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LottieAnimation(
            composition = composition,
            progress    = { progress },
            modifier    = Modifier.size(28.dp)
        )
        Text(
            text       = "YOUR TURN TO BAN",
            color      = MLBBRed,
            fontWeight = FontWeight.Bold,
            fontSize   = 11.sp
        )
    }
}

@Composable
private fun BanSlotRow(
    label: String,
    bans: List<Hero?>,
    r2Bans: List<Hero?>,
    banStructure: BanStructure,
    labelColor: androidx.compose.ui.graphics.Color = TextSecondary
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = labelColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bans.forEach { hero ->
                HeroPortrait(hero = hero, size = 42.dp)
            }
            if (banStructure.hasRound2) {
                Spacer(Modifier.width(6.dp))
                r2Bans.forEach { hero ->
                    HeroPortrait(hero = hero, size = 42.dp)
                }
            }
        }
    }
}

@Composable
private fun BanSuggestionRow(suggestions: List<BanSuggestion>, onTap: (Hero) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("🔥 RECOMMENDED BANS", color = MLBBGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.take(3).forEach { suggestion ->
                BanSuggestionCard(suggestion = suggestion, onTap = onTap)
            }
        }
    }
}

@Composable
private fun BanSuggestionCard(suggestion: BanSuggestion, onTap: (Hero) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(SurfaceCard, RoundedCornerShape(8.dp))
            .border(1.dp, MLBBRed.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .padding(6.dp)
            .width(72.dp)
    ) {
        HeroPortrait(hero = suggestion.hero, size = 52.dp, onClick = onTap)
        Spacer(Modifier.height(4.dp))
        Text(
            suggestion.hero.name, color = TextPrimary, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .background(MLBBRed.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(suggestion.badgeLabel, color = MLBBRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "%.0f%%".format(suggestion.score * 100),
            color = TextSecondary, fontSize = 8.sp
        )
    }
}
