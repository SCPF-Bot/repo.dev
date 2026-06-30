package com.mlbb.assistant.presentation.heropool

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.model.Proficiency
import com.mlbb.assistant.presentation.common.components.HeroPortrait
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroPoolScreen(
    onBack: () -> Unit,
    vm: HeroPoolViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hero_pool_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        if (state.entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text     = stringResource(R.string.hero_pool_empty),
                    color    = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top    = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start  = 16.dp,
                    end    = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.entries, key = { it.hero.id }) { entry ->
                    HeroPoolRow(
                        entry            = entry,
                        onSetProficiency = { prof -> vm.setHeroProficiency(entry.hero.id, prof) },
                        onRemove         = { vm.removeHero(entry.hero.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPoolRow(
    entry:            HeroPoolEntry,
    onSetProficiency: (Proficiency) -> Unit,
    onRemove:         () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceMid, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        HeroPortrait(hero = entry.hero, size = 44.dp)

        Column(Modifier.weight(1f)) {
            Text(
                text       = entry.hero.name,
                color      = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp
            )
            Text(
                text     = entry.hero.role,
                color    = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Proficiency chips
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ProficiencyChip.entries.forEach { chip ->
                ProficiencyToggle(
                    chip       = chip,
                    isSelected = entry.proficiency == chip.proficiency,
                    onClick    = { onSetProficiency(chip.proficiency) }
                )
            }
        }
    }
}

private enum class ProficiencyChip(
    val proficiency: Proficiency,
    val label:       String,
    val color:       Color
) {
    LEARNING(    Proficiency.LEARNING,    "L", Color(0xFFF59E0B)),
    COMFORTABLE( Proficiency.COMFORTABLE, "C", Color(0xFF10B981)),
    MASTERED(    Proficiency.MASTERED,    "M", Color(0xFF6366F1))
}

@Composable
private fun ProficiencyToggle(
    chip:       ProficiencyChip,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val bg by animateColorAsState(
        if (isSelected) chip.color.copy(alpha = 0.20f) else Color.Transparent,
        label = "proficiency_bg"
    )
    val border by animateColorAsState(
        if (isSelected) chip.color else TextDisabled.copy(alpha = 0.30f),
        label = "proficiency_border"
    )
    val a11y = "${chip.proficiency.name.lowercase().replaceFirstChar { it.uppercase() }} proficiency"

    Box(
        Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = a11y
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = chip.label,
            color      = if (isSelected) chip.color else TextDisabled,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
