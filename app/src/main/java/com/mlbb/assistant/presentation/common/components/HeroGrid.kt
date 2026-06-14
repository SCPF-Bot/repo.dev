package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.model.HeroRole
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun HeroGrid(
    heroes: List<Hero>,
    disabledIds: Set<Int>,
    onHeroTap: (Hero) -> Unit,
    onHeroLongPress: (Hero) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query      by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<String?>(null) }

    val filtered = remember(heroes, query, roleFilter) {
        heroes
            .filter { it.id !in disabledIds || true }  // show all, just grey disabled
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .filter { roleFilter == null || it.role.equals(roleFilter, ignoreCase = true) }
    }

    Column(modifier = modifier) {
        // Search bar
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (query.isEmpty()) {
                Text("🔍 Search hero...", color = TextDisabled, fontSize = 13.sp)
            }
            BasicTextField(
                value = query, onValueChange = { query = it },
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(6.dp))

        // Role filter chips
        val roles = listOf(null, "Tank", "Fighter", "Mage", "Marksman", "Support", "Assassin")
        ScrollableTabRow(
            selectedTabIndex = roles.indexOf(roleFilter).coerceAtLeast(0),
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = {}
        ) {
            roles.forEach { role ->
                val selected = roleFilter == role
                Tab(
                    selected = selected,
                    onClick  = { roleFilter = role },
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Box(
                        Modifier
                            .background(
                                if (selected) MLBBGold.copy(alpha = 0.25f) else SurfaceElevated,
                                RoundedCornerShape(16.dp)
                            )
                            .border(1.dp, if (selected) MLBBGold else SurfaceElevated, RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            role ?: "All",
                            color     = if (selected) MLBBGold else TextSecondary,
                            fontSize  = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filtered, key = { it.id }) { hero ->
                val disabled = hero.id in disabledIds
                HeroGridCell(
                    hero     = hero,
                    disabled = disabled,
                    onTap    = { if (!disabled) onHeroTap(hero) },
                    onLong   = { onHeroLongPress(hero) }
                )
            }
        }
    }
}

@Composable
private fun HeroGridCell(hero: Hero, disabled: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            .clickable { onTap() }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(5.dp))
        ) {
            AsyncImage(
                model = hero.imageUrl,
                contentDescription = hero.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (disabled) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)))
                Text(
                    "✕", color = TextDisabled, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Text(
            hero.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (disabled) TextDisabled else TextSecondary,
            fontSize = 8.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
