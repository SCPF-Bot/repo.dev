package com.mlbb.assistant.presentation.welcome

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlbb.assistant.presentation.common.theme.*

data class PermissionStep(
    val icon: String,
    val title: String,
    val description: String,
    val why: String,
    val actionLabel: String,
    val skipLabel: String = "Skip for now",
    val onAction: (android.content.Context) -> Unit
)

/**
 * Tries a list of candidate Intents in order, launching the first one that resolves.
 * Falls back to the last entry (which should always be a safe fallback like App Info).
 */
private fun tryStartActivity(context: android.content.Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

/**
 * Opens the manufacturer-specific auto-start / background-launch manager, falling back to
 * the standard App Info screen when no known OEM screen is available.
 *
 * Covers: Xiaomi MIUI, OPPO ColorOS, Vivo FuntouchOS, Huawei EMUI, OnePlus OxygenOS,
 * Samsung One UI, Realme UI, Meizu Flyme.
 */
private fun openAutoStartSettings(ctx: android.content.Context) {
    tryStartActivity(
        ctx,
        // Xiaomi MIUI
        Intent().setComponent(
            android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        ),
        // OPPO ColorOS
        Intent().setComponent(
            android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        ),
        // OPPO ColorOS (alternate)
        Intent().setComponent(
            android.content.ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        ),
        // Vivo FuntouchOS
        Intent().setComponent(
            android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        ),
        // Huawei EMUI
        Intent().setComponent(
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        ),
        // Huawei EMUI (alternate)
        Intent().setComponent(
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        ),
        // Samsung One UI (battery/sleep settings)
        Intent().setComponent(
            android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        ),
        // OnePlus OxygenOS
        Intent().setComponent(
            android.content.ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
        ),
        // Realme UI
        Intent().setComponent(
            android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.systemfloatwindow.FloatWindowListActivity"
            )
        ),
        // Meizu Flyme
        Intent().setComponent(
            android.content.ComponentName(
                "com.meizu.safe",
                "com.meizu.safe.permission.SmartPermissionActivity"
            )
        ),
        // Universal fallback: App Info
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")
        )
    )
}

/**
 * Opens the manufacturer-specific "background running" or "unrestricted background activity"
 * panel, falling back to App Info when no OEM screen is found.
 */
private fun openBackgroundRunningSettings(ctx: android.content.Context) {
    tryStartActivity(
        ctx,
        // Xiaomi MIUI — background activity
        Intent().setComponent(
            android.content.ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            )
        ),
        // Huawei EMUI — protected apps
        Intent().setComponent(
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        ),
        // Samsung — device care
        Intent().setComponent(
            android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        ),
        // Asus ROG / ZenFone
        Intent().setComponent(
            android.content.ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity"
            )
        ),
        // Generic: Battery optimisation details (all Android)
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}")
        ),
        // Universal fallback: App Info
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")
        )
    )
}

@Composable
fun PermissionWizardScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        // ── Step 1: Draw Over Other Apps ──────────────────────────────────────
        PermissionStep(
            icon        = "🖼️",
            title       = "Draw Over Other Apps",
            description = "Allows the draft assistant to float above MLBB while you play.",
            why         = "Without this, the overlay cannot appear on top of the game.",
            actionLabel = "Grant Permission",
            onAction    = { ctx ->
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        ),
        // ── Step 2: Accessibility Service ────────────────────────────────────
        PermissionStep(
            icon        = "♿",
            title       = "Accessibility Service",
            description = "Detects when MLBB is open and automatically shows the bubble.",
            why         = "Without this, you must launch the overlay manually from the app.",
            actionLabel = "Open Accessibility Settings",
            onAction    = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        ),
        // ── Step 3: Screen Capture ────────────────────────────────────────────
        PermissionStep(
            icon        = "📽️",
            title       = "Screen Capture",
            description = "Reads ban and pick portraits from the MLBB draft screen in real time.",
            why         = "Without this, hero detection is manual — tap each hero yourself.",
            actionLabel = "Allow When Prompted",
            skipLabel   = "Use Manual Mode",
            onAction    = { /* triggered at draft start via MediaProjection */ }
        ),
        // ── Step 4: Notifications ─────────────────────────────────────────────
        PermissionStep(
            icon        = "🔔",
            title       = "Notifications",
            description = "Shows a persistent notification while the overlay is running.",
            why         = "Required by Android to run background services reliably.",
            actionLabel = "Allow Notifications",
            onAction    = { ctx ->
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        ),
        // ── Step 5: Battery Optimisation ─────────────────────────────────────
        PermissionStep(
            icon        = "🔋",
            title       = "Disable Battery Optimisation",
            description = "Prevents Android from killing the overlay service when the screen is on.",
            why         = "Battery optimisation pauses background apps mid-draft, causing the assistant to go silent at the worst moment.",
            actionLabel = "Disable Battery Optimisation",
            skipLabel   = "Skip (may cause drops)",
            onAction    = { ctx ->
                val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                    try {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        ),
        // ── Step 6: App Auto-Start ────────────────────────────────────────────
        PermissionStep(
            icon        = "🚀",
            title       = "App Auto-Start",
            description = "Lets the overlay restart automatically if Android kills it.",
            why         = "On Xiaomi, OPPO, Vivo, Huawei and similar phones, apps without auto-start cannot relaunch their services — the bubble disappears and never comes back.",
            actionLabel = "Open Auto-Start Settings",
            skipLabel   = "My phone doesn't have this",
            onAction    = { ctx -> openAutoStartSettings(ctx) }
        ),
        // ── Step 7: Restricted Settings (Android 12+) ────────────────────────
        PermissionStep(
            icon        = "🔓",
            title       = "Unrestricted / Restricted Settings",
            description = "On Android 12 and newer, sideloaded apps need explicit permission to use certain protected features (overlay, accessibility).",
            why         = "If you installed this app outside the Play Store, Android may block the permissions above until you tap 'Allow restricted settings' in App Info.",
            actionLabel = "Open App Info",
            skipLabel   = "Installed from Play Store",
            onAction    = { ctx ->
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        ),
        // ── Step 8: Background Running ────────────────────────────────────────
        PermissionStep(
            icon        = "⚙️",
            title       = "Allow Background Running",
            description = "Keeps the draft assistant active while MLBB is in the foreground.",
            why         = "Without this, the overlay is suspended by the OS as soon as you switch to the game, defeating its purpose.",
            actionLabel = "Open Background Settings",
            skipLabel   = "Skip for now",
            onAction    = { ctx -> openBackgroundRunningSettings(ctx) }
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = SurfaceDark
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier  = Modifier.fillMaxWidth(0.90f),
                colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape     = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Step progress dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Step ${currentStep + 1} of ${steps.size}"
                        }
                    ) {
                        steps.indices.forEach { i ->
                            Box(
                                Modifier
                                    .size(if (i == currentStep) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (i == currentStep)
                                            Modifier.background(MLBBGold)
                                        else
                                            Modifier.background(SurfaceElevated)
                                    )
                            )
                        }
                    }

                    AnimatedContent(
                        targetState  = currentStep,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn() togetherWith fadeOut()
                            } else {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                            }
                        },
                        label = "wizard_step"
                    ) { step ->
                        val s = steps[step]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(s.icon, fontSize = 48.sp)
                            Text(
                                s.title, color = TextPrimary, fontWeight = FontWeight.Bold,
                                fontSize = 20.sp, textAlign = TextAlign.Center
                            )
                            Text(
                                s.description, color = TextSecondary, fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Surface(
                                modifier       = Modifier.fillMaxWidth(),
                                color          = InfoBlue.copy(alpha = 0.10f),
                                shape          = RoundedCornerShape(8.dp),
                                tonalElevation = 0.dp
                            ) {
                                Text(
                                    "Why: ${s.why}",
                                    color    = InfoBlue,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    val step = steps[currentStep]
                    Button(
                        onClick = {
                            step.onAction(context)
                            if (currentStep < steps.lastIndex) currentStep++ else onComplete()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = MLBBGold)
                    ) {
                        Text(step.actionLabel, color = SurfaceDark)
                    }
                    OutlinedButton(
                        onClick = {
                            if (currentStep < steps.lastIndex) currentStep++ else onComplete()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(step.skipLabel)
                    }

                    Text(
                        "${currentStep + 1} of ${steps.size}",
                        color = TextDisabled, fontSize = 11.sp
                    )
                }
            }
        }
    }
}
