package com.mlbb.assistant.presentation.welcome

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private fun tryStartActivity(context: android.content.Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

private fun openAutoStartSettings(ctx: android.content.Context) {
    val launchedByLibrary = runCatching {
        com.judemanutd.autostarter.AutoStartPermissionHelper
            .getInstance()
            .getAutoStartPermission(ctx)
    }.getOrDefault(false)
    if (launchedByLibrary) return

    tryStartActivity(
        ctx,
        Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(android.content.ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.systemfloatwindow.FloatWindowListActivity")),
        Intent().setComponent(android.content.ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartPermissionActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    )
}

private fun openBackgroundRunningSettings(ctx: android.content.Context) {
    tryStartActivity(
        ctx,
        Intent().setComponent(android.content.ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(android.content.ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")),
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
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

    val steps = remember {
        buildList {
            add(PermissionStep(
                icon        = "🖼️",
                title       = "Draw Over Other Apps",
                description = "Allows the draft assistant to float above MLBB while you play.",
                why         = "Without this, the overlay cannot appear on top of the game.",
                actionLabel = "Grant Permission",
                onAction    = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ))

            add(PermissionStep(
                icon        = "⚙️",
                title       = "Open New Windows in Background",
                description = "Keeps the draft assistant active while MLBB is in the foreground.",
                why         = "Without this, the overlay is suspended by the OS as soon as you switch to the game.",
                actionLabel = "Open Background Settings",
                skipLabel   = "Skip for now",
                onAction    = { ctx -> openBackgroundRunningSettings(ctx) }
            ))

            add(PermissionStep(
                icon        = "⚡",
                title       = "Background Start Activity",
                description = "Grants the app permission to open overlay windows while MLBB is in the foreground.",
                why         = "Without this AppOps grant, Android silently blocks the overlay. In App Info go to Battery → select \"Unrestricted\".",
                actionLabel = "Open App Info → Battery",
                skipLabel   = "Skip (overlay may not open in-game)",
                onAction    = { ctx ->
                    tryStartActivity(
                        ctx,
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
                        Intent(Settings.ACTION_SETTINGS)
                    )
                }
            ))

            add(PermissionStep(
                icon        = "🚀",
                title       = "App Auto-Start",
                description = "Lets the overlay restart automatically if Android kills it.",
                why         = "On Xiaomi, OPPO, Vivo, Huawei and similar phones, apps without auto-start cannot relaunch their services.",
                actionLabel = "Open Auto-Start Settings",
                skipLabel   = "My phone doesn't have this",
                onAction    = { ctx -> openAutoStartSettings(ctx) }
            ))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionStep(
                    icon        = "🔓",
                    title       = "Restricted Settings",
                    description = "On Android 13 and newer, sideloaded apps need explicit permission to use certain protected features.",
                    why         = "If you installed this app outside the Play Store, Android may block permissions until you tap 'Allow restricted settings' in App Info.",
                    actionLabel = "Open App Info",
                    skipLabel   = "Installed from Play Store",
                    onAction    = { ctx ->
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ))
            }

            add(PermissionStep(
                icon        = "♿",
                title       = "Accessibility Service",
                description = "Detects when MLBB is open and automatically shows the bubble.",
                why         = "Without this, you must launch the overlay manually from the app.",
                actionLabel = "Open Accessibility Settings",
                onAction    = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ))
        }
    }

    // ── Full screen background ─────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A18), SurfaceDark, Color(0xFF0D1018))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, MLBBGold.copy(alpha = 0.20f), RoundedCornerShape(20.dp)),
            colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape     = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            // Gold accent strip at top of card
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MLBBGold.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Progress dots ──────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Step ${currentStep + 1} of ${steps.size}"
                    }
                ) {
                    steps.indices.forEach { i ->
                        val isActive = i == currentStep
                        val isDone   = i < currentStep
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (isActive) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isActive -> MLBBGold
                                        isDone   -> MLBBGold.copy(alpha = 0.45f)
                                        else     -> SurfaceElevated
                                    }
                                )
                        )
                    }
                }

                // ── Step content ───────────────────────────────────────────
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
                        // Icon in branded circle
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MLBBGold.copy(alpha = 0.10f))
                                .border(1.dp, MLBBGold.copy(alpha = 0.30f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(s.icon, fontSize = 32.sp)
                        }

                        Text(
                            s.title,
                            color      = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 20.sp,
                            textAlign  = TextAlign.Center
                        )
                        Text(
                            s.description,
                            color     = TextSecondary,
                            fontSize  = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        // "Why" info box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(InfoBlue.copy(alpha = 0.08f))
                                .border(1.dp, InfoBlue.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "Why: ${s.why}",
                                color    = InfoBlue,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // ── Action buttons ─────────────────────────────────────────
                val step = steps[currentStep]
                Button(
                    onClick = {
                        step.onAction(context)
                        if (currentStep < steps.lastIndex) currentStep++ else onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MLBBGold),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        step.actionLabel,
                        color      = SurfaceDark,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        letterSpacing = 0.3.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (currentStep < steps.lastIndex) currentStep++ else onComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Text(step.skipLabel, color = TextSecondary)
                }

                Text(
                    "${currentStep + 1} of ${steps.size}",
                    color = TextDisabled,
                    fontSize = 11.sp
                )
            }
        }
    }
}
