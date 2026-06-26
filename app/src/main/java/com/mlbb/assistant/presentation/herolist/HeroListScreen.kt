package com.mlbb.assistant.presentation.herolist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.components.HeroGrid
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.valentinilk.shimmer.shimmer

/** Number of shimmer placeholder tiles shown while heroes load. */
private const val SHIMMER_TILE_COUNT = 18
private const val SHIMMER_TILE_SIZE_DP = 72

@Composable
fun HeroListScreen(
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    // Pass 4 / UX fix: collectAsStateWithLifecycle — lifecycle-aware flow collection.
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Header — proper back button with 48dp touch target
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BackButton(onBack = onBack)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null,
                    tint = MLBBGold, modifier = Modifier.size(18.dp))
                Text("HERO EXPLORER", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            // Balance the back button width
            Spacer(Modifier.size(48.dp))
        }

        when {
            state.isLoading -> HeroLoadingSkeleton()

            state.heroes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Person, contentDescription = null,
                        tint = TextDisabled, modifier = Modifier.size(48.dp))
                    Text("No heroes loaded", color = TextSecondary)
                    Text("Pull to refresh or check connection", color = TextDisabled, fontSize = 12.sp)
                }
            }

            else -> HeroGrid(
                heroes      = state.filteredHeroes.ifEmpty { state.heroes },
                disabledIds = emptySet(),
                onHeroTap   = { hero -> onHeroClick(hero) },
                modifier    = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
        }
    }
}

/**
 * Shimmer skeleton grid shown while the hero list is loading.
 *
 * Renders [SHIMMER_TILE_COUNT] placeholder tiles in the same grid geometry
 * as [HeroGrid] so the transition from skeleton → real content is smooth
 * with no layout shift. Each tile matches the 72 dp adaptive cell size.
 *
 * Uses [com.valentinilk.shimmer.shimmer] (valentinilk/compose-shimmer 1.3.0).
 * The `shimmer()` modifier animates the gradient sweep across the entire grid
 * by applying a single shared [com.valentinilk.shimmer.ShimmerInstance] so all
 * tiles pulse in synchrony rather than each running an independent animation.
 */
@Composable
private fun HeroLoadingSkeleton() {
    LazyVerticalGrid(
        columns             = GridCells.Adaptive(SHIMMER_TILE_SIZE_DP.dp),
        modifier            = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .shimmer(),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SHIMMER_TILE_COUNT) {
            Box(
                modifier = Modifier
                    .size(SHIMMER_TILE_SIZE_DP.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceMid)
            )
        }
    }
}
