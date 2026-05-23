package com.example.mlbbdraftassistant.ui.overlay

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.*
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
    val context = LocalContext.current

    // ----- Collapsed bubble (when !expanded) -----
    AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        )
                    )
                )
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            // Show top recommendation icon or generic icon
            val topRec = state.recommendations.firstOrNull()
            if (topRec != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(topRec.hero.hero_image ?: "")
                        .crossfade(true)
                        .build(),
                    contentDescription = topRec.hero.hero_name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // ----- Expanded panel -----
    AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MLBB Draft Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    // Detection mode toggle
                    Text(
                        text = if (state.detectionMode == DetectionMode.OCR) "OCR" else "Icon",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    IconButton(onClick = onToggleDetectionMode) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Toggle Mode")
                    }
                    IconButton(onClick = { expanded = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Collapse")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Capture button with Lottie pulse when ready, loading animation when capturing
            CaptureButton(
                isLoading = state.isLoading,
                isReady = state.isCaptureReady,
                onClick = onCapture
            )

            // Error text
            if (state.detectionError != null) {
                Text(
                    text = state.detectionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Draft slots (collapsible section)
            var showSlots by remember { mutableStateOf(false) }
            TextButton(onClick = { showSlots = !showSlots }) {
                Text(if (showSlots) "Hide Picks ▲" else "Show Picks ▼")
            }

            AnimatedVisibility(visible = showSlots) {
                Column {
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                            Text("Reset")
                        }
                        Button(
                            onClick = onLockToggle,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isLocked)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (state.isLocked) "Unlock" else "Lock")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recommendations list
            Text(
                "Top Recommendations",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (state.recommendations.isEmpty()) {
                Text(
                    "Select heroes or tap Detect",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.recommendations) { rec ->
                        RecommendationItem(rec)
                    }
                }
            }
        }
    }
}

// ------- Capture Button with Lottie animations -------
@Composable
fun CaptureButton(
    isLoading: Boolean,
    isReady: Boolean,
    onClick: () -> Unit
) {
    val idleAnimation by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.pulse_animation)
    )
    val loadingAnimation by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.loading_animation)
    )

    IconButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .size(56.dp)
            .align(Alignment.CenterHorizontally)
            .background(
                if (isReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else Color.Transparent,
                CircleShape
            )
    ) {
        when {
            isLoading -> {
                LottieAnimation(
                    composition = loadingAnimation,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(32.dp)
                )
            }
            isReady -> {
                LottieAnimation(
                    composition = idleAnimation,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(32.dp)
                )
            }
            else -> {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Detect",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ------- Recommendation Item (with expandable breakdown) -------
@Composable
fun RecommendationItem(rec: Recommendation) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDetails = !showDetails },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hero icon
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(rec.hero.hero_image ?: "")
                        .crossfade(true)
                        .build(),
                    contentDescription = rec.hero.hero_name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rec.hero.hero_name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Score: %.2f".format(rec.totalScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                // Expand icon
                Icon(
                    if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Brief reason (always visible)
            Text(
                rec.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // Expanded breakdown
            AnimatedVisibility(visible = showDetails, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    DetailRow("Synergy", rec.synergyScore)
                    DetailRow("Counter", rec.counterScore)
                    DetailRow("Role Balance", rec.roleBalanceScore)
                    DetailRow("Meta", rec.metaScore)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, score: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { score },
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (score > 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        Text("%.2f".format(score), style = MaterialTheme.typography.bodySmall)
    }
}

// ------- Hero Dropdown (unchanged, but included for completeness) -------
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true
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
