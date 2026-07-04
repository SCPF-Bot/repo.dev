package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextSecondary

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
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .filter { roleFilter == null || it.role.equals(roleFilter, ignoreCase = true) }
    }

    // Adaptive grid columns based on screen width instead of fixed 5
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = when {
        screenWidthDp >= 840 -> 9   // large tablet / desktop
        screenWidthDp >= 600 -> 7   // tablet / foldable
        else                 -> 5   // phone
    }

    Column(modifier = modifier) {
        // M3 OutlinedTextField replaces BasicTextField — has hint, icon, and IME action
        OutlinedTextField(
            value           = query,
            onValueChange   = { query = it },
            placeholder     = { Text("Search heroes…") },
            leadingIcon     = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon    = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            singleLine      = true,
            // Pass 1 fix: KeyboardOptions moved so import is alphabetically ordered
            // (androidx.compose.foundation.text before androidx.compose.ui.text.input).
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier        = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search heroes" }
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))

        // FilterChip replaces custom Tab — correct M3 component for single-select filters.
        // FilterChip has built-in selected-state semantics announced by TalkBack.
        val roles = listOf<String?>(null, "Tank", "Fighter", "Mage", "Marksman", "Support", "Assassin")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(horizontal = 2.dp)
        ) {
            items(roles, key = { it ?: "__all__" }) { role ->
                FilterChip(
                    selected = roleFilter == role,
                    onClick  = { roleFilter = role },
                    label    = { Text(role ?: "All") }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))

        LazyVerticalGrid(
            columns               = GridCells.Fixed(columns),
            verticalArrangement   = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier              = Modifier.fillMaxWidth()
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
private fun HeroGridCell(
    hero:     Hero,
    disabled: Boolean,
    onTap:    () -> Unit,
    onLong:   () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            // combinedClickable: onClick + long-press properly wired
            .combinedClickable(
                onClick          = onTap,
                onLongClick      = onLong,
                onLongClickLabel = "View ${hero.name} options"
            )
            .padding(3.dp)
            // Parent-level semantics so image and label are one accessible unit
            .semantics {
                contentDescription = if (disabled) "${hero.name}, unavailable" else hero.name
            }
    ) {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(5.dp))
        ) {
            AsyncImage(
                model              = android.net.Uri.parse("file:///android_asset/portraits/${hero.id}.webp"),
                contentDescription = null,   // parent semantics covers this
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
            if (disabled) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Block,
                        contentDescription = null,
                        tint               = Color.White.copy(alpha = 0.7f),
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            hero.name,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            color     = if (disabled) TextDisabled else TextSecondary,
            fontSize  = 8.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}
