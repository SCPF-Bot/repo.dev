package com.mlbb.assistant.presentation.welcome

import android.content.Intent
import android.net.Uri
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

@Composable
fun PermissionWizardScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    // Respect system reduce-motion preference (ANIMATOR_DURATION_SCALE == 0)
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        PermissionStep(
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
        ),
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
        PermissionStep(
            icon        = "📽️",
            title       = "Screen Capture",
            description = "Reads ban and pick portraits from the MLBB draft screen in real time.",
            why         = "Without this, hero detection is manual — tap each hero yourself.",
            actionLabel = "Allow When Prompted",
            skipLabel   = "Use Manual Mode",
            onAction    = { /* triggered at draft start via MediaProjection */ }
        ),
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
                    // Step progress dots with accessibility semantics
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                    // Animated step content — respects reduce-motion preference
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
                            Text(s.title, color = TextPrimary, fontWeight = FontWeight.Bold,
                                fontSize = 20.sp, textAlign = TextAlign.Center)
                            Text(s.description, color = TextSecondary, fontSize = 14.sp,
                                textAlign = TextAlign.Center)
                            Surface(
                                modifier      = Modifier.fillMaxWidth(),
                                color         = InfoBlue.copy(alpha = 0.10f),
                                shape         = RoundedCornerShape(8.dp),
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

                    // Action buttons — M3 Button / OutlinedButton for proper a11y semantics
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

