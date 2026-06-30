package com.mlbb.assistant.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.presentation.common.components.BackButton
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.MLBBGold
import com.mlbb.assistant.presentation.common.theme.SurfaceCard
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.common.theme.SurfaceMid
import com.mlbb.assistant.presentation.common.theme.TextPrimary
import com.mlbb.assistant.presentation.common.theme.TextSecondary
import com.mlbb.assistant.presentation.settings.components.AspectRatioSection
import com.mlbb.assistant.presentation.settings.components.BanCountRow
import com.mlbb.assistant.presentation.settings.components.BanPhaseScreenshotSection
import com.mlbb.assistant.presentation.settings.components.CalibrationSection
import com.mlbb.assistant.presentation.settings.components.InfoRow
import com.mlbb.assistant.presentation.settings.components.PermissionRow
import com.mlbb.assistant.presentation.settings.components.ScreenMappingDialog
import com.mlbb.assistant.presentation.settings.components.SectionDivider
import com.mlbb.assistant.presentation.settings.components.ScoringWeightsSection
import com.mlbb.assistant.presentation.settings.components.SettingsSection
import com.mlbb.assistant.presentation.settings.components.SliderRow
import com.mlbb.assistant.presentation.settings.components.ToggleRow
import com.mlbb.assistant.utils.DevLoggerManager

/**
 * Settings screen root. This is a thin orchestrator that delegates each
 * settings section to its own composable in `components/`:
 *  - [ScoringWeightsSection]       — meta/counter/synergy sliders + reset
 *  - [CalibrationSection]          — auto-calibration result display
 *  - [BanCountRow]                 — ban phase count dropdown
 *  - [BanPhaseScreenshotSection]   — screenshot picker + mapping buttons
 */
@Composable
fun SettingsScreen(
    onBack:         () -> Unit,
    onOpenHeroPool: () -> Unit = {},
    viewModel:      SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showResetDialog   by remember { mutableStateOf(false) }
    var showMappingDialog by remember { mutableStateOf(false) }

    // ── Dialogs ───────────────────────────────────────────────────────────────

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

    // ── Root layout ───────────────────────────────────────────────────────────

    Column(Modifier.fillMaxSize().background(SurfaceDark)) {

        // Header
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
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Screen shape ──────────────────────────────────────────────
            SettingsSection(
                icon     = Icons.Rounded.AspectRatio,
                title    = "SCREEN",
                subtitle = "Helps the overlay find the right spots on your screen"
            ) {
                AspectRatioSection(
                    selected   = state.aspectRatioPreset,
                    onSelected = { viewModel.setAspectRatioPreset(it) }
                )
            }

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
                state            = state,
                onShowReset      = { showResetDialog = true },
                onMetaChanged    = { viewModel.setMetaWeight(it) },
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
            SettingsSection(
                icon     = Icons.Rounded.Shield,
                title    = "BAN PHASE",
                subtitle = "Determines how many portrait slots the overlay monitors"
            ) {
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

            // ── Logs ──────────────────────────────────────────────────────
            SettingsSection(
                icon     = Icons.Rounded.BugReport,
                title    = "LOGS",
                subtitle = "Diagnostic and crash log settings"
            ) {
                val context = LocalContext.current
                ToggleRow(
                    label   = "Developer",
                    checked = state.developerModeEnabled,
                    onToggle = { enabled ->
                        viewModel.setDeveloperMode(enabled)
                        if (enabled) {
                            DevLoggerManager.promptInstallIfNeeded(context, force = true)
                        }
                    }
                )
                if (state.developerModeEnabled) {
                    SectionDivider()
                    val loggerInstalled = DevLoggerManager.isInstalled(context)
                    InfoRow(
                        label = "Verbose logger",
                        value = if (loggerInstalled) "Installed" else "Not installed"
                    )
                    if (!loggerInstalled) {
                        TextButton(onClick = {
                            DevLoggerManager.promptInstallIfNeeded(context, force = true)
                        }) {
                            Text("Install logger app", color = MLBBGold, fontSize = 12.sp)
                        }
                    } else {
                        TextButton(onClick = { DevLoggerManager.launch(context) }) {
                            Text("Open logger app", color = MLBBGold, fontSize = 12.sp)
                        }
                    }
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
                            Intent(
                                SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
