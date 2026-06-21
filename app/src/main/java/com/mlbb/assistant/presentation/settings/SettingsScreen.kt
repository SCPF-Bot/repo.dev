package com.mlbb.assistant.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil3.compose.AsyncImage
import com.mlbb.assistant.R
import com.mlbb.assistant.domain.engine.WeightCalibrator
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.MLBBGoldDark
import com.mlbb.assistant.presentation.common.theme.MLBBTeal
import com.mlbb.assistant.presentation.common.theme.SuccessGreen
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceElevated
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextDisabled
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.common.theme.WarningAmber
import com.mlbb.assistant.presentation.settings.components.PermissionRow
import com.mlbb.assistant.presentation.settings.components.ScreenMappingDialog
import com.mlbb.assistant.presentation.settings.components.SectionDivider
import com.mlbb.assistant.presentation.settings.components.SettingsSection
import com.mlbb.assistant.presentation.settings.components.SliderRow
import com.mlbb.assistant.presentation.settings.components.ToggleRow
import com.mlbb.assistant.presentation.settings.components.InfoRow
import com.mlbb.assistant.presentation.settings.components.parseMappedPoints
import kotlin.math.abs

// ── Ban-count options ─────────────────────────────────────────────────────────

private val BAN_COUNT_OPTIONS = listOf(
    "6 bans (Epic)",
    "8 bans (Legend)",
    "10 bans (Mythic and higher)"
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHeroPool: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResetDialog    by remember { mutableStateOf(false) }
    var showMappingDialog  by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor   = SurfaceCard,
            title  = { Text("Reset scoring weights?", color = TextPrimary) },
            text   = { Text("Weights will be restored to defaults: Meta 40%, Counter 30%, Synergy 30%.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetWeights(); showResetDialog = false }) {
                    Text("Reset", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (showMappingDialog && state.banPhaseScreenshotUri.isNotBlank()) {
        ScreenMappingDialog(
            screenshotUri  = state.banPhaseScreenshotUri,
            initialMapping = state.screenMappingJson,
            onDismiss      = { showMappingDialog = false },
            onSave         = { json -> viewModel.setScreenMapping(json); showMappingDialog = false }
        )
    }

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {
        // ── Header ────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(SurfaceMid, SurfaceDark)))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                BackButton(onBack = onBack)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(18.dp))
                    Text("SETTINGS", color = MLBBGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.size(48.dp))
            }
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Overlay ───────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Tune, title = "OVERLAY") {
                SliderRow("Opacity", state.overlayOpacity, 0.3f..1f) { viewModel.setOpacity(it) }
                SectionDivider()
                ToggleRow("Auto-show when MLBB is detected", state.autoShowOverlay) { viewModel.setAutoShow(it) }
                SectionDivider()
                ToggleRow("Voice alerts", state.voiceAlertsEnabled) { viewModel.setVoiceAlerts(it) }
            }

            // ── Scoring weights ───────────────────────────────────────────
            ScoringWeightsSection(
                state          = state,
                onShowReset    = { showResetDialog = true },
                onMetaChanged  = { viewModel.setMetaWeight(it) },
                onCounterChanged = { viewModel.setCounterWeight(it) },
                onSynergyChanged = { viewModel.setSynergyWeight(it) }
            )

            // ── Calibration ───────────────────────────────────────────────
            CalibrationSection(
                result        = state.calibrationResult,
                isCalibrating = state.isCalibrating,
                onRefresh     = { viewModel.runCalibration() },
                onApply       = { viewModel.applyCalibrationWeights() }
            )

            // ── Ban phase ─────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Shield, title = "BAN PHASE",
                subtitle = "Determines how many portrait slots the overlay monitors") {
                BanCountRow(current = state.defaultRank, onSelected = { viewModel.setDefaultRank(it) })
            }

            // ── Ban phase screenshot ──────────────────────────────────────
            BanPhaseScreenshotSection(
                currentUri        = state.banPhaseScreenshotUri,
                screenMappingJson = state.screenMappingJson,
                onUriSelected     = { viewModel.setBanPhaseScreenshotUri(it) },
                onClearUri        = { viewModel.setBanPhaseScreenshotUri("") },
                onOpenMapping     = { showMappingDialog = true },
                onClearMapping    = { viewModel.setScreenMapping("") }
            )

            // ── Data ──────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Sync, title = "DATA") {
                ToggleRow("Auto-sync meta data", state.autoSync) { viewModel.setAutoSync(it) }
                SectionDivider()
                InfoRow("Last synced", state.lastSyncedLabel)
                TextButton(onClick = { viewModel.syncNow() }) {
                    Text("Sync now", color = MLBBGold, fontSize = 12.sp)
                }
            }

            // ── Permissions ───────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Lock, title = "PERMISSIONS") {
                val context = LocalContext.current
                PermissionRow(
                    label   = "Draw over other apps",
                    granted = state.overlayGranted,
                    onClick = {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                SectionDivider()
                PermissionRow(
                    label   = "Accessibility service",
                    granted = state.accessibilityGranted,
                    onClick = {
                        context.startActivity(
                            Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Scoring weights section ───────────────────────────────────────────────────

@Composable
private fun ScoringWeightsSection(
    state:            SettingsState,
    onShowReset:      () -> Unit,
    onMetaChanged:    (Float) -> Unit,
    onCounterChanged: (Float) -> Unit,
    onSynergyChanged: (Float) -> Unit
) {
    SettingsSection(icon = Icons.Rounded.GridOn, title = "SCORING WEIGHTS", subtitle = "Weights must sum to 100%") {
        val weightSum = state.metaWeight + state.counterWeight + state.synergyWeight
        val balanced  = abs(weightSum - 1f) < 0.01f

        SliderRow("Meta strength",  state.metaWeight,    0f..1f, onMetaChanged)
        SectionDivider()
        SliderRow("Counter value",  state.counterWeight, 0f..1f, onCounterChanged)
        SectionDivider()
        SliderRow("Synergy value",  state.synergyWeight, 0f..1f, onSynergyChanged)

        AnimatedVisibility(visible = !balanced) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .background(ErrorRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(15.dp))
                Text(
                    "Weights sum to ${"%.0f".format(weightSum * 100)}% — adjust to reach 100%",
                    color    = ErrorRed,
                    fontSize = 12.sp
                )
            }
        }

        TextButton(onClick = onShowReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Reset to defaults", color = TextDisabled, fontSize = 12.sp)
        }
    }
}

// ── Calibration section ───────────────────────────────────────────────────────

@Composable
private fun CalibrationSection(
    result:        WeightCalibrator.CalibrationResult?,
    isCalibrating: Boolean,
    onRefresh:     () -> Unit,
    onApply:       () -> Unit
) {
    SettingsSection(icon = Icons.Rounded.Info, title = stringResource(R.string.calibration_title)) {
        when {
            isCalibrating -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = MLBBGold, modifier = Modifier.size(22.dp))
                }
            }
            result == null -> {
                Text(stringResource(R.string.calibration_need_more), color = TextSecondary, fontSize = 12.sp)
            }
            else -> {
                Text(stringResource(R.string.calibration_rationale_label), color = MLBBGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(result.rationale, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                SectionDivider()
                Spacer(Modifier.height(6.dp))
                val confPct  = (result.confidence * 100).toInt()
                val confDesc = "${stringResource(R.string.calibration_confidence_label)}: $confPct%"
                Text(confDesc, color = TextSecondary, fontSize = 11.sp)
                LinearProgressIndicator(
                    progress   = { result.confidence },
                    modifier   = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = confDesc },
                    color      = MLBBTeal,
                    trackColor = SurfaceElevated
                )
                val sw = result.suggestedWeights
                Text(
                    "Suggested — Meta ${"%.0f".format(sw.meta * 100)}%  Counter ${"%.0f".format(sw.counter * 100)}%  Synergy ${"%.0f".format(sw.synergy * 100)}%",
                    color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRefresh) { Text("Refresh", color = TextDisabled, fontSize = 12.sp) }
                    TextButton(onClick = onApply) { Text(stringResource(R.string.calibration_apply), color = MLBBGold, fontSize = 12.sp) }
                }
            }
        }
    }
}

// ── Ban count selector ────────────────────────────────────────────────────────

@Composable
private fun BanCountRow(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ban count", color = TextSecondary, fontSize = 12.sp)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, MLBBGold.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Ban count: $current. Tap to change." },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(current, color = MLBBGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector        = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint               = TextSecondary,
                    modifier           = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BAN_COUNT_OPTIONS.forEach { option ->
                    val selected = option == current
                    DropdownMenuItem(
                        text = {
                            Text(option, color = if (selected) MLBBGold else TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                        },
                        trailingIcon = if (selected) ({
                            Icon(Icons.Rounded.Done, contentDescription = null, tint = MLBBGold, modifier = Modifier.size(16.dp))
                        }) else null,
                        onClick = { onSelected(option); expanded = false }
                    )
                }
            }
        }
        Text(
            "Sets how many hero portrait positions the overlay tracks during the ban phase.",
            color = TextDisabled, fontSize = 11.sp
        )
    }
}

// ── Ban phase screenshot section ──────────────────────────────────────────────

@Composable
private fun BanPhaseScreenshotSection(
    currentUri:        String,
    screenMappingJson: String,
    onUriSelected:     (String) -> Unit,
    onClearUri:        () -> Unit,
    onOpenMapping:     () -> Unit,
    onClearMapping:    () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onUriSelected(it.toString()) }
    }
    val mappedCount = remember(screenMappingJson) { parseMappedPoints(screenMappingJson).size }

    SettingsSection(icon = Icons.Rounded.Image, title = "BAN PHASE REFERENCE",
        subtitle = "Screenshot used for hero portrait detection") {
        if (currentUri.isBlank()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(SurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .border(1.dp, SurfaceElevated, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(32.dp))
                Text("No screenshot selected", color = TextDisabled, fontSize = 13.sp)
                Text(
                    "Select a screenshot of the ban phase to let the app detect portrait locations.",
                    color = TextDisabled, fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth()
                    .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .border(1.dp, SuccessGreen.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(Uri.parse(currentUri).lastPathSegment ?: "Screenshot",
                        color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (mappedCount > 0) {
                        Text("$mappedCount portrait position${if (mappedCount > 1) "s" else ""} mapped",
                            color = MLBBTeal, fontSize = 11.sp)
                    } else {
                        Text("No positions mapped yet", color = WarningAmber, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onClearUri, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove screenshot", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = { launcher.launch(arrayOf("image/*")) },
                modifier = Modifier.weight(1f),
                border   = androidx.compose.foundation.BorderStroke(1.dp, MLBBGold.copy(alpha = 0.4f)),
                shape    = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = MLBBGold)
                Spacer(Modifier.width(6.dp))
                Text(if (currentUri.isBlank()) "Select Screenshot" else "Replace", color = MLBBGold, fontSize = 13.sp)
            }
            Button(
                onClick  = onOpenMapping,
                enabled  = currentUri.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = if (mappedCount > 0) MLBBTeal else MLBBGoldDark,
                    disabledContainerColor = SurfaceElevated
                )
            ) {
                Icon(Icons.Rounded.GridOn, contentDescription = null, modifier = Modifier.size(15.dp),
                    tint = if (currentUri.isNotBlank()) SurfaceDark else TextDisabled)
                Spacer(Modifier.width(6.dp))
                Text(if (mappedCount > 0) "Remap ($mappedCount)" else "Map Screen",
                    color = if (currentUri.isNotBlank()) SurfaceDark else TextDisabled, fontSize = 13.sp)
            }
        }

        if (mappedCount > 0) {
            TextButton(onClick = onClearMapping, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(13.dp), tint = ErrorRed)
                Spacer(Modifier.width(4.dp))
                Text("Clear mapping", color = ErrorRed, fontSize = 12.sp)
            }
        }
    }
}
