package com.mlbb.assistant.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
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

                // Weight balance indicator — shown when sum ≠ 100%
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

            // Draft preferences
            SettingsSection("DRAFT PREFERENCES") {
                InfoRow("Default rank", state.defaultRank)
            }

            // Data
            SettingsSection("DATA") {
                ToggleRow("Auto-sync meta data", state.autoSync) { viewModel.setAutoSync(it) }
                InfoRow("Last synced", state.lastSyncedLabel)
                TextButton(onClick = { viewModel.syncNow() }) {
                    Text("Sync now", color = MLBBGold, fontSize = 12.sp)
                }
            }

            // Permissions — with actionable deep-link icons
            SettingsSection("PERMISSIONS") {
                PermissionRow(
                    label   = "Overlay",
                    granted = state.overlayGranted
                )
                PermissionRow(
                    label   = "Accessibility",
                    granted = state.accessibilityGranted
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

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
            // Slider contentDescription so TalkBack reads the current percentage
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
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector        = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                contentDescription = if (granted) "$label granted" else "$label not granted",
                tint               = if (granted) SuccessGreen else ErrorRed,
                modifier           = Modifier.size(16.dp)
            )
            Text(
                if (granted) "Granted" else "Not granted",
                color      = if (granted) SuccessGreen else ErrorRed,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
