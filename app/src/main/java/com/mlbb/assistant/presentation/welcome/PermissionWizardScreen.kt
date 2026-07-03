package com.mlbb.assistant.presentation.welcome

import android.content.ComponentName
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

// ── Intent helpers ────────────────────────────────────────────────────────────

/**
 * Returns true if [pkg] is installed on this device.
 * Used to skip OEM intents whose package is not present before attempting them,
 * avoiding silent swallowing of ActivityNotFoundException on unrelated devices.
 */
private fun packageExists(ctx: android.content.Context, pkg: String): Boolean =
    runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

/**
 * Tries each [Intent] in order, stopping at the first one that launches successfully.
 * Exceptions (ActivityNotFoundException, SecurityException) are swallowed silently
 * so the next candidate is always attempted.
 */
private fun tryStartActivity(ctx: android.content.Context, vararg intents: Intent) {
    for (intent in intents) {
        runCatching {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
}

/**
 * Opens the most appropriate battery/background-running settings screen for the
 * current device. Checks package existence before constructing each intent so no
 * unnecessary ActivityNotFoundExceptions are thrown on unrelated OEMs.
 *
 * Priority:
 * 1. OEM-specific battery/background-app manager (most granular control)
 * 2. Standard Android "ignore battery optimizations" direct dialog
 * 3. App Info screen (universal guaranteed fallback)
 */
private fun openBatterySettings(ctx: android.content.Context) {
    val pkg = ctx.packageName
    val intents = buildList {
        // Xiaomi / MIUI / HyperOS
        if (packageExists(ctx, "com.miui.powerkeeper"))
            add(Intent().setComponent(ComponentName("com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")))

        // Huawei / Honor / HarmonyOS
        if (packageExists(ctx, "com.huawei.systemmanager")) {
            add(Intent().setComponent(ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity")))
            add(Intent().setComponent(ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
        }

        // Samsung / One UI
        if (packageExists(ctx, "com.samsung.android.lool"))
            add(Intent().setComponent(ComponentName("com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity")))

        // OPPO / ColorOS (covers Realme which runs ColorOS)
        if (packageExists(ctx, "com.coloros.safecenter")) {
            add(Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
            add(Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.systemfloatwindow.FloatWindowListActivity")))
        }
        if (packageExists(ctx, "com.oppo.safe"))
            add(Intent().setComponent(ComponentName("com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity")))

        // Vivo / FuntouchOS / OriginOS
        if (packageExists(ctx, "com.vivo.permissionmanager"))
            add(Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))

        // iQOO (Vivo sub-brand, different package)
        if (packageExists(ctx, "com.iqoo.secure"))
            add(Intent().setComponent(ComponentName("com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))

        // OnePlus / OxygenOS
        if (packageExists(ctx, "com.oneplus.security"))
            add(Intent().setComponent(ComponentName("com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")))

        // ASUS / ZenFone
        if (packageExists(ctx, "com.asus.mobilemanager"))
            add(Intent().setComponent(ComponentName("com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity")))

        // Meizu / Flyme
        if (packageExists(ctx, "com.meizu.safe"))
            add(Intent().setComponent(ComponentName("com.meizu.safe",
                "com.meizu.safe.permission.SmartPermissionActivity")))

        // Tecno / Infinix / Itel (HiOS / XOS — Transsion Group)
        if (packageExists(ctx, "com.transsion.phonemaster"))
            add(Intent().setComponent(ComponentName("com.transsion.phonemaster",
                "com.transsion.phonemaster.business.appdetail.AppDetailActivity")))

        // Standard Android — direct "ignore battery optimizations" dialog
        add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$pkg")))

        // Universal fallback: App Info (always works)
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$pkg")))
    }
    tryStartActivity(ctx, *intents.toTypedArray())
}

/**
 * Opens the auto-start / startup manager screen for the current OEM.
 *
 * Tries the AutoStarter library first (maintained list of 30+ OEM intents).
 * Falls back to a curated manual list with package-existence guards so no
 * spurious exceptions are thrown on devices that don't have these packages.
 */
private fun openAutoStartSettings(ctx: android.content.Context) {
    val launchedByLibrary = runCatching {
        com.judemanutd.autostarter.AutoStartPermissionHelper
            .getInstance()
            .getAutoStartPermission(ctx)
    }.getOrDefault(false)
    if (launchedByLibrary) return

    val pkg = ctx.packageName
    val intents = buildList {
        // Xiaomi / MIUI / HyperOS
        if (packageExists(ctx, "com.miui.securitycenter"))
            add(Intent().setComponent(ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")))

        // Huawei / Honor
        if (packageExists(ctx, "com.huawei.systemmanager"))
            add(Intent().setComponent(ComponentName("com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))

        // OPPO / ColorOS / Realme
        if (packageExists(ctx, "com.coloros.safecenter"))
            add(Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
        if (packageExists(ctx, "com.oppo.safe"))
            add(Intent().setComponent(ComponentName("com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity")))

        // Vivo / FuntouchOS / OriginOS
        if (packageExists(ctx, "com.vivo.permissionmanager"))
            add(Intent().setComponent(ComponentName("com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))

        // iQOO (Vivo sub-brand)
        if (packageExists(ctx, "com.iqoo.secure"))
            add(Intent().setComponent(ComponentName("com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))

        // Samsung
        if (packageExists(ctx, "com.samsung.android.lool"))
            add(Intent().setComponent(ComponentName("com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity")))

        // OnePlus / OxygenOS
        if (packageExists(ctx, "com.oneplus.security"))
            add(Intent().setComponent(ComponentName("com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")))

        // Meizu / Flyme
        if (packageExists(ctx, "com.meizu.safe"))
            add(Intent().setComponent(ComponentName("com.meizu.safe",
                "com.meizu.safe.permission.SmartPermissionActivity")))

        // Tecno / Infinix / Itel (HiOS)
        if (packageExists(ctx, "com.transsion.phonemaster"))
            add(Intent().setComponent(ComponentName("com.transsion.phonemaster",
                "com.transsion.phonemaster.business.appdetail.AppDetailActivity")))

        // Universal fallback
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$pkg")))
    }
    tryStartActivity(ctx, *intents.toTypedArray())
}

// ── Wizard screen ─────────────────────────────────────────────────────────────

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
            // Step 1 — Overlay (always required)
            add(PermissionStep(
                icon        = "🖼️",
                title       = "Draw Over Other Apps",
                description = "Allows the draft assistant to float above MLBB while you play.",
                why         = "Without this, the overlay cannot appear on top of the game.",
                actionLabel = "Grant Permission",
                onAction    = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ))

            // Step 2 — Battery & Background (merged from old steps 2+3)
            // Opens the most relevant OEM battery screen; falls back to the
            // standard Android "ignore battery optimizations" dialog, then App Info.
            add(PermissionStep(
                icon        = "🔋",
                title       = "Battery & Background",
                description = "Keeps the overlay running while MLBB is in the foreground.",
                why         = "Most Android OEMs aggressively kill background apps to save battery. " +
                              "Set this app to \"Unrestricted\" or add it to the protected list so the overlay stays alive.",
                actionLabel = "Open Battery Settings",
                skipLabel   = "Skip for now",
                onAction    = { ctx -> openBatterySettings(ctx) }
            ))

            // Step 3 — Auto-Start (OEM-specific; skippable on stock Android)
            add(PermissionStep(
                icon        = "🚀",
                title       = "App Auto-Start",
                description = "Lets the overlay restart automatically if Android kills it.",
                why         = "On Xiaomi, OPPO, Vivo, Huawei, Samsung and similar phones, apps without " +
                              "auto-start permission cannot relaunch their background services after being killed.",
                actionLabel = "Open Auto-Start Settings",
                skipLabel   = "My phone doesn't have this",
                onAction    = { ctx -> openAutoStartSettings(ctx) }
            ))

            // Step 4 — Restricted Settings (Android 13+ / sideloaded only)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionStep(
                    icon        = "🔓",
                    title       = "Restricted Settings",
                    description = "On Android 13 and newer, sideloaded apps need explicit permission to use certain protected features.",
                    why         = "If you installed this app outside the Play Store, Android may block " +
                                  "permissions until you tap \"Allow restricted settings\" in App Info.",
                    actionLabel = "Open App Info",
                    skipLabel   = "Installed from Play Store",
                    onAction    = { ctx ->
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ))
            }

            // Step 5 — Accessibility Service (always last; most sensitive)
            add(PermissionStep(
                icon        = "♿",
                title       = "Accessibility Service",
                description = "Detects when MLBB is open and automatically shows the overlay bubble.",
                why         = "Without this, you must launch the overlay manually every session. " +
                              "No gameplay data, keystrokes, or personal input is read.",
                actionLabel = "Open Accessibility Settings",
                onAction    = { ctx ->
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ))
        }
    }

    // ── Full-screen background ─────────────────────────────────────────────────
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
                    targetState = currentStep,
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
                            color      = TextSecondary,
                            fontSize   = 14.sp,
                            textAlign  = TextAlign.Center,
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
                                color      = InfoBlue,
                                fontSize   = 12.sp,
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
                        color         = SurfaceDark,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 14.sp,
                        letterSpacing = 0.3.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (currentStep < steps.lastIndex) currentStep++ else onComplete()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(step.skipLabel, color = TextSecondary)
                }

                Text(
                    "${currentStep + 1} of ${steps.size}",
                    color    = TextDisabled,
                    fontSize = 11.sp
                )
            }
        }
    }
}
