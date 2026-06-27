package com.mlbb.assistant.presentation.overlay.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBRed
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary

// ── Turn badge ────────────────────────────────────────────────────────────────

@Composable
internal fun TurnBadge(text: String, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

// ── Ban recommendations ───────────────────────────────────────────────────────

@Composable
internal fun BanRecommendedRow(heroes: List<Pair<Hero, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "RECOMMENDED BANS",
            color         = MLBBRed.copy(alpha = 0.8f),
            fontSize      = 7.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
        val row1 = heroes.take(4)
        val row2 = heroes.drop(4).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            row1.forEach { (hero, badge) -> BanHeroChip(hero, badge) }
        }
        if (row2.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row2.forEach { (hero, badge) -> BanHeroChip(hero, badge) }
            }
        }
    }
}

@Composable
private fun BanHeroChip(hero: Hero, badge: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MLBBRed.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
            .border(0.5.dp, MLBBRed.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .width(44.dp)
    ) {
        Text(hero.name, color = TextPrimary, fontSize = 6.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(badge, color = MLBBRed, fontSize = 6.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ── Pick recommendations ──────────────────────────────────────────────────────

@Composable
internal fun PickRecommendedRow(heroes: List<Pair<Hero, String>>, onHeroSelected: (Hero) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "RECOMMENDED PICKS",
            color         = MLBBGold,
            fontSize      = 7.sp,
            fontWeight    = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
        val row1 = heroes.take(3)
        val row2 = heroes.drop(3).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row1.forEach { (hero, badge) -> QuickHeroChip(hero, badge, onHeroSelected) }
        }
        if (row2.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row2.forEach { (hero, badge) -> QuickHeroChip(hero, badge, onHeroSelected) }
            }
        }
    }
}

@Composable
internal fun QuickHeroChip(hero: Hero, badge: String, onTap: (Hero) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(SurfaceMid, RoundedCornerShape(7.dp))
            .clickable { onTap(hero) }
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .width(52.dp)
    ) {
        HeroPortrait(hero = hero, size = 36.dp)
        Spacer(Modifier.height(1.dp))
        Text(hero.name, color = TextPrimary, fontSize = 6.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(badge, color = MLBBGold, fontSize = 6.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
