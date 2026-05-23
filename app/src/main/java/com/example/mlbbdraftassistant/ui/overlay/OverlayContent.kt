package com.example.mlbbdraftassistant.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.domain.Recommendation

@Composable
fun OverlayContent(
    state: DraftState,
    onAllySelected: (slot: Int, hero: Hero) -> Unit,
    onEnemySelected: (slot: Int, hero: Hero) -> Unit,
    onReset: () -> Unit,
    onLockToggle: () -> Unit,
    onCapture: () -> Unit,
    onToggleDetectionMode: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Draft Picks", style = MaterialTheme.typography.titleMedium)
            Row {
                // Detection mode toggle
                Text(
                    text = if (state.detectionMode == DetectionMode.OCR) "OCR" else "Icon",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                IconButton(onClick = onToggleDetectionMode) {
                    Icon(Icons.Default.Settings, contentDescription = "Toggle Mode")
                }
                // Capture button
                IconButton(onClick = onCapture, enabled = !state.isLoading) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Detect Draft")
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle"
                    )
                }
            }
        }

        // Error display
        if (state.detectionError != null) {
            Text(
                state.detectionError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (expanded) {
            Text("Your Team", style = MaterialTheme.typography.labelMedium)
            for (slot in 0..4) {
                HeroDropdown(
                    label = "Ally ${slot + 1}",
                    selectedHero = state.allies.getOrNull(slot),
                    availableHeroes = state.availableHeroes,
                    onHeroSelected = { hero -> onAllySelected(slot, hero) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Enemy Team", style = MaterialTheme.typography.labelMedium)
            for (slot in 0..4) {
                HeroDropdown(
                    label = "Enemy ${slot + 1}",
                    selectedHero = state.enemies.getOrNull(slot),
                    availableHeroes = state.availableHeroes,
                    onHeroSelected = { hero -> onEnemySelected(slot, hero) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
                Button(
                    onClick = onLockToggle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isLocked) MaterialTheme.colorScheme.error
                                         else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (state.isLocked) "Unlock" else "Lock")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Recommendations
        Text("Top Recommendations", style = MaterialTheme.typography.titleMedium)
        if (state.recommendations.isEmpty()) {
            Text(
                "Select heroes or tap Detect to see suggestions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                items(state.recommendations) { rec ->
                    RecommendationItem(rec)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDropdown(
    label: String,
    selectedHero: Hero?,
    availableHeroes: List<Hero>,
    onHeroSelected: (Hero) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(selectedHero?.hero_name ?: "") }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            readOnly = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val filtered = availableHeroes.filter {
                it.hero_name.contains(searchText, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No heroes found") },
                    onClick = { expanded = false }
                )
            } else {
                filtered.forEach { hero ->
                    DropdownMenuItem(
                        text = { Text(hero.hero_name) },
                        onClick = {
                            searchText = hero.hero_name
                            onHeroSelected(hero)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationItem(rec: Recommendation) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rec.hero.hero_name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Score: %.2f".format(rec.totalScore),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                rec.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}