package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun HeroPortrait(
    hero: Hero?,
    size: Dp = 56.dp,
    showName: Boolean = false,
    showTier: Boolean = false,
    isMissedBan: Boolean = false,
    isDisabled: Boolean = false,
    onClick: ((Hero) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        hero == null && isMissedBan -> OverlayMissedBan
        hero != null -> tierBorderColor(hero.tier)
        else -> OverlayBanSlotEmpty
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (hero == null) OverlayBanSlotEmpty else SurfaceCard)
            .border(1.5.dp, borderColor, shape)
            .then(if (hero != null && onClick != null && !isDisabled)
                Modifier.clickable { onClick(hero) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            isMissedBan && hero == null -> {
                Text("✕", color = ErrorRed, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
            }
            hero == null -> {
                Text("?", color = TextDisabled, fontSize = (size.value * 0.35f).sp)
            }
            else -> {
                AsyncImage(
                    model = hero.imageUrl,
                    contentDescription = hero.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isDisabled) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier)
                )
                if (showTier) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .background(tierBorderColor(hero.tier).copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(hero.tier.display, color = SurfaceDark, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showName && hero != null) {
        Spacer(Modifier.height(3.dp))
        Text(
            hero.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp, color = TextSecondary,
            modifier = Modifier.width(size)
        )
    }
}

@Composable
fun EmptyPortraitSlot(size: Dp = 56.dp, isMissedBan: Boolean = false) {
    HeroPortrait(hero = null, size = size, isMissedBan = isMissedBan)
}

private fun tierBorderColor(tier: Tier) = when (tier) {
    Tier.S_PLUS -> TierSPlus
    Tier.S      -> TierS
    Tier.A_PLUS -> TierAPlus
    Tier.A      -> TierA
    Tier.B      -> TierB
}
