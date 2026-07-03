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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.valentinilk.shimmer.shimmer

private const val SHIMMER_TILE_COUNT = 18
private const val SHIMMER_TILE_SIZE_DP = 80

private val HERO_ROLES = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Support", "Assassin")

@Composable
fun HeroListScreen(
    onHeroClick: (Hero) -> Unit,
    onBack: () -> Unit,
    viewModel: HeroListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagedHeroes = viewModel.pagedHeroes.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // ── Header with gradient ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1A2E), SurfaceMid)
                    )
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                BackButton(onBack = onBack)
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint     = MLBBGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "HERO EXPLORER",
                        color         = MLBBGold,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 16.sp,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.size(48.dp))
            }
        }

        // ── Search bar ──────────────────────────────────────────────────────
        OutlinedTextField(
            value         = state.searchQuery,
            onValueChange = viewModel::onSearchQuery,
            placeholder   = { Text("Search heroes…", color = TextDisabled) },
            leadingIcon   = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MLBBGold.copy(alpha = 0.7f)) },
            trailingIcon  = if (state.searchQuery.isNotEmpty()) {
                { IconButton(onClick = { viewModel.onSearchQuery("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search", tint = TextSecondary)
                }}
            } else null,
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MLBBGold.copy(alpha = 0.7f),
                unfocusedBorderColor = TextDisabled.copy(alpha = 0.4f),
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                cursorColor          = MLBBGold
            ),
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // ── Role filter chips ───────────────────────────────────────────────
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(horizontal = 12.dp),
            modifier              = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            items(HERO_ROLES, key = { it ?: "__all__" }) { role ->
                FilterChip(
                    selected = state.selectedRole == role,
                    onClick  = { viewModel.onRoleFilter(role) },
                    label    = { Text(role ?: "All", fontWeight = if (state.selectedRole == role) FontWeight.Bold else FontWeight.Normal) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor    = MLBBGold.copy(alpha = 0.20f),
                        selectedLabelColor        = MLBBGold,
                        selectedLeadingIconColor  = MLBBGold
                    ),
                    border   = FilterChipDefaults.filterChipBorder(
                        enabled          = true,
                        selected         = state.selectedRole == role,
                        selectedBorderColor   = MLBBGold.copy(alpha = 0.6f),
                        disabledBorderColor   = TextDisabled.copy(alpha = 0.3f),
                        disabledSelectedBorderColor = TextDisabled.copy(alpha = 0.3f),
                        borderColor       = TextDisabled.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // ── Content area ────────────────────────────────────────────────────
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
                            tint = TextDisabled, modifier = Modifier.size(52.dp))
                        Text("No heroes found", color = TextSecondary, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun PagedHeroGrid(
    pagedHeroes: LazyPagingItems<Hero>,
    onHeroClick: (Hero) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(SHIMMER_TILE_SIZE_DP.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding        = PaddingValues(12.dp),
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceMid)
                )
            }
        }

        if (pagedHeroes.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
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
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .clickable(onClick = onTap)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
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
            fontSize  = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth().padding(top = 3.dp)
        )
    }
}

@Composable
private fun HeroLoadingSkeleton() {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(SHIMMER_TILE_SIZE_DP.dp),
        modifier              = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .shimmer(),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SHIMMER_TILE_COUNT) {
            Box(
                modifier = Modifier
                    .size(SHIMMER_TILE_SIZE_DP.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceMid)
            )
        }
    }
}
