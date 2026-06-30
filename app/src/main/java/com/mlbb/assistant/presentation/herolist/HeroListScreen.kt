package com.mlbb.assistant.presentation.herolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.valentinilk.shimmer.shimmer

private const val SHIMMER_TILE_COUNT = 18
private const val SHIMMER_TILE_SIZE_DP = 72

private val HERO_ROLES = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Support", "Assassin")

@Composable
fun HeroListScreen(
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // TD-10: Collect as paged items — lifecycle-aware, cancels on composition exit.
    val pagedHeroes = viewModel.pagedHeroes.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // Header
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
            Spacer(Modifier.size(48.dp))
        }

        // Search bar — drives ViewModel's searchQueryFlow → pagedHeroes refreshes
        OutlinedTextField(
            value         = state.searchQuery,
            onValueChange = viewModel::onSearchQuery,
            placeholder   = { Text("Search heroes…") },
            leadingIcon   = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon  = if (state.searchQuery.isNotEmpty()) {
                { IconButton(onClick = { viewModel.onSearchQuery("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                }}
            } else null,
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier        = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // Role filter chips — drives ViewModel's selectedRoleFlow → pagedHeroes refreshes
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(horizontal = 10.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            items(HERO_ROLES, key = { it ?: "__all__" }) { role ->
                FilterChip(
                    selected = state.selectedRole == role,
                    onClick  = { viewModel.onRoleFilter(role) },
                    label    = { Text(role ?: "All") }
                )
            }
        }

        // Content area
        when {
            state.isLoading -> HeroLoadingSkeleton()

            pagedHeroes.itemCount == 0 &&
            pagedHeroes.loadState.refresh !is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null,
                            tint = TextDisabled, modifier = Modifier.size(48.dp))
                        Text("No heroes loaded", color = TextSecondary)
                        Text("Pull to refresh or check connection", color = TextDisabled, fontSize = 12.sp)
                    }
                }
            }

            else -> PagedHeroGrid(
                pagedHeroes = pagedHeroes,
                onHeroClick = onHeroClick
            )
        }
    }
}

/**
 * TD-10: Paged hero grid using [LazyPagingItems].
 *
 * Items are fetched in pages from Room's PagingSource via [HeroListViewModel.pagedHeroes].
 * The grid adapts cell width to screen size using [GridCells.Adaptive]. A loading footer
 * (gold spinner) is appended while the next page fetches, providing smooth infinite-scroll.
 *
 * Placeholder cells (null items during prefetch) render as grey skeleton boxes matching
 * the real cell size so there is no layout shift when data arrives.
 */
@Composable
private fun PagedHeroGrid(
    pagedHeroes: LazyPagingItems<Hero>,
    onHeroClick: (Hero) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(SHIMMER_TILE_SIZE_DP.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding        = PaddingValues(10.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        items(count = pagedHeroes.itemCount) { index ->
            val hero = pagedHeroes[index]
            if (hero != null) {
                HeroPagedCell(hero = hero, onTap = { onHeroClick(hero) })
            } else {
                Box(
                    Modifier
                        .size(SHIMMER_TILE_SIZE_DP.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceMid)
                )
            }
        }

        // Append loading footer — shows while the next page is being fetched
        if (pagedHeroes.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color    = MLBBGold,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPagedCell(hero: Hero, onTap: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceMid)
            .clickable(onClick = onTap)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(5.dp))
        ) {
            AsyncImage(
                model              = hero.imageUrl,
                contentDescription = hero.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        Text(
            hero.name,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            color     = TextSecondary,
            fontSize  = 8.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Shimmer skeleton grid shown while the hero list is loading.
 *
 * Renders [SHIMMER_TILE_COUNT] placeholder tiles in the same grid geometry
 * as the paged grid so the transition from skeleton → real content is smooth.
 */
@Composable
private fun HeroLoadingSkeleton() {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(SHIMMER_TILE_SIZE_DP.dp),
        modifier              = Modifier
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
