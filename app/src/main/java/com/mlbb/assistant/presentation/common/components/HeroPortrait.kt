package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.Tier
import com.mlbb.assistant.presentation.common.theme.*
import com.valentinilk.shimmer.shimmer

/**
 * HeroPortrait wraps itself in a Column so [showName] is self-contained and does
 * not rely on the parent being a Column (previous fragile pattern).
 *
 * Accessibility improvements:
 * - Parent Column carries a merged contentDescription covering both portrait + name
 * - Tier badge increased from 8sp → 10sp minimum for readability
 * - Empty/missed slots use Material Icons instead of emoji text ("✕" / "?")
 *
 * Image loading (recommendations.md §8.1):
 * - Uses [SubcomposeAsyncImage] from Coil 3 to gain access to the loading state.
 * - A [shimmer] modifier animates a gradient sweep over a [SurfaceMid] placeholder
 *   Box while the hero portrait loads from the network.
 * - Error state falls back to the [QuestionMark] icon rather than a broken-image
 *   indicator, matching the visual language of empty portrait slots.
 */
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
    val semanticLabel = when {
        hero == null && isMissedBan -> "Missed ban slot"
        hero == null                -> "Empty slot"
        isDisabled                  -> "${hero.name}, ${hero.tier.display} tier, unavailable"
        else                        -> "${hero.name}, ${hero.tier.display} tier"
    }

    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        hero == null && isMissedBan -> OverlayMissedBan
        hero != null                -> tierBorderColor(hero.tier)
        else                        -> OverlayBanSlotEmpty
    }

    // Column wraps portrait + optional name so showName is self-contained
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = semanticLabel
        }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (hero == null) OverlayBanSlotEmpty else SurfaceCard)
                .border(1.5.dp, borderColor, shape)
                .then(
                    if (hero != null && onClick != null && !isDisabled)
                        Modifier.clickable(onClickLabel = "Select ${hero.name}") { onClick(hero) }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isMissedBan && hero == null -> {
                    Icon(
                        imageVector        = Icons.Rounded.Close,
                        contentDescription = null,   // parent semantics covers "Missed ban slot"
                        tint               = ErrorRed,
                        modifier           = Modifier.size(size * 0.4f)
                    )
                }
                hero == null -> {
                    Icon(
                        imageVector        = Icons.Rounded.QuestionMark,
                        contentDescription = null,   // parent semantics covers "Empty slot"
                        tint               = TextDisabled,
                        modifier           = Modifier.size(size * 0.35f)
                    )
                }
                else -> {
                    if (hero.imageUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model              = hero.imageUrl,
                            contentDescription = null,   // parent semantics covers hero name + tier
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                            loading = {
                                // Shimmer placeholder while network image loads.
                                // Uses the parent Box clip so no additional clipping needed.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .shimmer()
                                        .background(SurfaceMid)
                                )
                            },
                            error = {
                                // Graceful fallback on load failure — mirrors the empty slot icon.
                                Icon(
                                    imageVector        = Icons.Rounded.QuestionMark,
                                    contentDescription = null,
                                    tint               = TextDisabled,
                                    modifier           = Modifier.size(size * 0.45f)
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector        = Icons.Rounded.QuestionMark,
                            contentDescription = null,
                            tint               = TextDisabled,
                            modifier           = Modifier.size(size * 0.45f)
                        )
                    }
                    // Disabled overlay drawn on top of the image
                    if (isDisabled) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    }
                    if (showTier) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .background(tierBorderColor(hero.tier).copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                hero.tier.display,
                                color      = SurfaceDark,
                                fontSize   = 10.sp,           // was 8sp — increased to minimum readable size
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showName && hero != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                hero.name,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                fontSize  = 10.sp,
                color     = if (isDisabled) TextDisabled else TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.width(size)
            )
        }
    }
}

@Composable
fun EmptyPortraitSlot(size: Dp = 56.dp, isMissedBan: Boolean = false) {
    HeroPortrait(hero = null, size = size, isMissedBan = isMissedBan)
}

// Internal — used by HeroPortrait and HeroDetailScreen
fun tierBorderColor(tier: Tier): androidx.compose.ui.graphics.Color = when (tier) {
    Tier.S_PLUS  -> TierSPlus
    Tier.S       -> TierS
    Tier.A_PLUS  -> TierAPlus
    Tier.A       -> TierA
    Tier.B       -> TierB
    Tier.UNKNOWN -> TierB    // fallback — display at lowest tier colour
}
