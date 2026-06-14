package com.mlbb.assistant.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mlbb.assistant.presentation.common.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier.fillMaxSize().background(SurfaceDark)
    ) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceMid).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚙️ SETTINGS", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("← Back", color = MLBBGold, fontSize = 12.sp, modifier = Modifier.clickable { onBack() })
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Overlay settings
            SettingsSection("OVERLAY") {
                SliderRow("Opacity", state.overlayOpacity, 0.3f..1f) { viewModel.setOpacity(it) }
                ToggleRow("Auto-show when MLBB detected", state.autoShowOverlay) { viewModel.setAutoShow(it) }
                ToggleRow("Voice alerts", state.voiceAlertsEnabled) { viewModel.setVoiceAlerts(it) }
            }

            // Scoring weights
            SettingsSection("SCORING WEIGHTS") {
                SliderRow("Meta strength",  state.metaWeight,    0f..1f) { viewModel.setMetaWeight(it) }
                SliderRow("Counter value",  state.counterWeight, 0f..1f) { viewModel.setCounterWeight(it) }
                SliderRow("Synergy value",  state.synergyWeight, 0f..1f) { viewModel.setSynergyWeight(it) }
                TextButton(onClick = { viewModel.resetWeights() }) {
                    Text("Reset to defaults", color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Draft preferences
            SettingsSection("DRAFT PREFERENCES") {
                val ranks = listOf("Epic","Legend","Mythic","Mythical Honor","Mythical Glory","Immortal")
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

            // Permissions status
            SettingsSection("PERMISSIONS") {
                InfoRow("Overlay", if (state.overlayGranted) "✅ Granted" else "❌ Not granted")
                InfoRow("Accessibility", if (state.accessibilityGranted) "✅ Granted" else "❌ Not granted")
            }
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
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChanged: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text("%.0f%%".format(value * 100), color = MLBBGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value, onValueChange = onChanged, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = MLBBGold, activeTrackColor = MLBBGold, inactiveTrackColor = SurfaceElevated)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = MLBBGold, checkedTrackColor = MLBBGold.copy(0.35f))
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
