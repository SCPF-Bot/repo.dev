package com.mlbb.assistant.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.engine.WeightCalibrator
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

private val MLBB_RANKS = listOf(
    "Warrior", "Elite", "Master", "Grandmaster", "Epic",
    "Legend", "Mythic", "Mythical Honor", "Mythical Glory", "Mythical Immortal"
)

@Composable
fun SettingsScreen(
    onBack:         () -> Unit,
    onOpenHeroPool: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title   = { Text("Reset scoring weights?") },
            text    = { Text("Weights will be restored to defaults: Meta 40%, Counter 30%, Synergy 30%.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetWeights(); showResetDialog = false }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMid)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            BackButton(onBack = onBack)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = null,
                    tint = MLBBGold, modifier = Modifier.size(18.dp))
                Text("SETTINGS", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.size(48.dp))
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Overlay
            SettingsSection("OVERLAY") {
                SliderRow(
                    label     = "Opacity",
                    value     = state.overlayOpacity,
                    range     = 0.3f..1f,
                    onChanged = { viewModel.setOpacity(it) }
                )
                ToggleRow("Auto-show when MLBB detected", state.autoShowOverlay) { viewModel.setAutoShow(it) }
                ToggleRow("Voice alerts",                  state.voiceAlertsEnabled) { viewModel.setVoiceAlerts(it) }
            }

            // Scoring weights
            SettingsSection("SCORING WEIGHTS") {
                val weightSum  = state.metaWeight + state.counterWeight + state.synergyWeight
                val balanced   = abs(weightSum - 1f) < 0.01f

                SliderRow("Meta strength",  state.metaWeight,    0f..1f) { viewModel.setMetaWeight(it) }
                SliderRow("Counter value",  state.counterWeight, 0f..1f) { viewModel.setCounterWeight(it) }
                SliderRow("Synergy value",  state.synergyWeight, 0f..1f) { viewModel.setSynergyWeight(it) }

                AnimatedVisibility(visible = !balanced) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp))
                        Text(
                            "Weights sum to ${"%.0f".format(weightSum * 100)}% — adjust to reach 100%",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                TextButton(
                    onClick  = { showResetDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reset to defaults", color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Section 5.2.2 — Calibration transparency
            CalibrationSection(
                result        = state.calibrationResult,
                isCalibrating = state.isCalibrating,
                onRefresh     = { viewModel.runCalibration() },
                onApply       = { viewModel.applyCalibrationWeights() }
            )

            // Draft preferences
            SettingsSection("DRAFT PREFERENCES") {
                RankSelectorRow(
                    currentRank = state.defaultRank,
                    onRankSelected = { viewModel.setDefaultRank(it) }
                )
            }

            // Ban phase reference screenshot
            BanPhaseScreenshotSection(
                currentUri = state.banPhaseScreenshotUri,
                onUriSelected = { viewModel.setBanPhaseScreenshotUri(it) }
            )

            // Widget score descriptions JSON
            ScoreDescriptionsJsonSection(
                currentUri = state.scoreDescriptionsJsonUri,
                onUriSelected = { viewModel.setScoreDescriptionsJsonUri(it) }
            )

            // Data
            SettingsSection("DATA") {
                ToggleRow("Auto-sync meta data", state.autoSync) { viewModel.setAutoSync(it) }
                InfoRow("Last synced", state.lastSyncedLabel)
                TextButton(onClick = { viewModel.syncNow() }) {
                    Text("Sync now", color = MLBBGold, fontSize = 12.sp)
                }
            }

            // Permissions
            SettingsSection("PERMISSIONS") {
                val context = LocalContext.current
                PermissionRow(
                    label   = "Overlay",
                    granted = state.overlayGranted,
                    onClick = {
                        context.startActivity(
                            Intent(
                                SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                PermissionRow(
                    label   = "Accessibility",
                    granted = state.accessibilityGranted,
                    onClick = {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Rank selector ─────────────────────────────────────────────────────────────

@Composable
private fun RankSelectorRow(currentRank: String, onRankSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Default rank", color = TextSecondary, fontSize = 13.sp)

        Box {
            Row(
                modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Default rank: $currentRank. Tap to change." },
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(currentRank, color = MLBBGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector        = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint               = TextSecondary,
                    modifier           = Modifier.size(16.dp)
                )
            }
            DropdownMenu(
                expanded         = expanded,
                onDismissRequest = { expanded = false }
            ) {
                MLBB_RANKS.forEach { rank ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                rank,
                                color      = if (rank == currentRank) MLBBGold else TextPrimary,
                                fontWeight = if (rank == currentRank) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        },
                        onClick = {
                            onRankSelected(rank)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ── Ban phase reference screenshot ───────────────────────────────────────────

@Composable
private fun BanPhaseScreenshotSection(currentUri: String, onUriSelected: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onUriSelected(it.toString()) }
    }

    SettingsSection("BAN PHASE REFERENCE SCREENSHOT") {
        Text(
            "Point the app to a screenshot you took of the ban phase. The overlay uses this image as a pixel-map reference for hero portrait matching.",
            color    = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(4.dp))

        if (currentUri.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null,
                    tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Text(
                    Uri.parse(currentUri).lastPathSegment ?: currentUri,
                    color    = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { launcher.launch(arrayOf("image/*")) },
                colors  = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = MLBBGold)
                Spacer(Modifier.size(6.dp))
                Text(
                    if (currentUri.isBlank()) "Select Screenshot" else "Replace",
                    color    = TextPrimary,
                    fontSize = 13.sp
                )
            }
            if (currentUri.isNotBlank()) {
                TextButton(onClick = { onUriSelected("") }) {
                    Text("Clear", color = ErrorRed, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Widget score descriptions JSON ────────────────────────────────────────────

@Composable
private fun ScoreDescriptionsJsonSection(currentUri: String, onUriSelected: (String) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onUriSelected(it.toString()) }
    }

    SettingsSection("WIDGET SCORE DESCRIPTIONS") {
        Text(
            "Select a JSON file containing custom text descriptions for each score level. The overlay widget reads this file instead of the built-in defaults.",
            color    = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(4.dp))

        if (currentUri.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null,
                    tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Text(
                    Uri.parse(currentUri).lastPathSegment ?: currentUri,
                    color    = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
        } else {
            Text(
                "Using built-in defaults",
                color    = TextDisabled,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { launcher.launch(arrayOf("application/json", "text/plain")) },
                colors  = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = MLBBGold)
                Spacer(Modifier.size(6.dp))
                Text(
                    if (currentUri.isBlank()) "Select JSON File" else "Replace",
                    color    = TextPrimary,
                    fontSize = 13.sp
                )
            }
            if (currentUri.isNotBlank()) {
                TextButton(onClick = { onUriSelected("") }) {
                    Text("Clear", color = ErrorRed, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Section 5.2.2 — Calibration transparency card ────────────────────────────

@Composable
private fun CalibrationSection(
    result:        WeightCalibrator.CalibrationResult?,
    isCalibrating: Boolean,
    onRefresh:     () -> Unit,
    onApply:       () -> Unit
) {
    SettingsSection(stringResource(R.string.calibration_title)) {
        when {
            isCalibrating -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color    = MLBBGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            result == null -> {
                Text(
                    stringResource(R.string.calibration_need_more),
                    color    = TextSecondary,
                    fontSize = 12.sp
                )
            }
            else -> {
                Text(
                    stringResource(R.string.calibration_rationale_label),
                    color      = MLBBGold,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.rationale,
                    color    = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = SurfaceElevated)
                Spacer(Modifier.height(4.dp))

                val confPct  = (result.confidence * 100).toInt()
                val confDesc = "${stringResource(R.string.calibration_confidence_label)}: $confPct%"
                Text(confDesc, color = TextSecondary, fontSize = 11.sp)
                LinearProgressIndicator(
                    progress   = { result.confidence },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = confDesc },
                    color      = MLBBTeal,
                    trackColor = SurfaceElevated
                )

                Spacer(Modifier.height(4.dp))

                val sw = result.suggestedWeights
                Text(
                    "Suggested: Meta ${"%.0f".format(sw.meta * 100)}%  " +
                    "Counter ${"%.0f".format(sw.counter * 100)}%  " +
                    "Synergy ${"%.0f".format(sw.synergy * 100)}%",
                    color      = TextPrimary,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh", color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onApply) {
                        Text(
                            stringResource(R.string.calibration_apply),
                            color = MLBBGold, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Shared primitives ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = MLBBGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChanged: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text("%.0f%%".format(value * 100), color = MLBBGold,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value         = value,
            onValueChange = onChanged,
            valueRange    = range,
            colors        = SliderDefaults.colors(
                thumbColor         = MLBBGold,
                activeTrackColor   = MLBBGold,
                inactiveTrackColor = SurfaceElevated
            ),
            modifier = Modifier.semantics {
                contentDescription = "$label, ${value.times(100).roundToInt()} percent"
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor = MLBBGold,
                checkedTrackColor = MLBBGold.copy(0.35f)
            )
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value,  color = TextPrimary,  fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "$label permission, ${if (granted) "granted" else "not granted"}, tap to open settings" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector        = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                contentDescription = null,
                tint               = if (granted) SuccessGreen else ErrorRed,
                modifier           = Modifier.size(16.dp)
            )
            Text(
                if (granted) "Granted" else "Tap to enable",
                color      = if (granted) SuccessGreen else ErrorRed,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
