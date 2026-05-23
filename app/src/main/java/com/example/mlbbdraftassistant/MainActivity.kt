package com.example.mlbbdraftassistant

import androidx.preference.PreferenceManager
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.preference.PreferenceManager

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                if (hasScreenCapturePermission()) {
                    checkDisclaimerAndProceed()
                } else {
                    requestScreenCapture()
                }
            } else {
                setContent {
                    MaterialTheme {
                        Surface {
                            PermissionDeniedScreen(
                                onOpenSettings = { openAppSettings() },
                                onCancel = { finish() }
                            )
                        }
                    }
                }
            }
        }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SET_MEDIA_PROJECTION
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(serviceIntent)
                finish()
            } else {
                Toast.makeText(this, "Screen capture permission is required for auto-detection", Toast.LENGTH_LONG).show()
                checkDisclaimerAndProceed()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            if (hasScreenCapturePermission()) {
                checkDisclaimerAndProceed()
            } else {
                requestScreenCapture()
            }
        } else {
            setContent {
                MaterialTheme {
                    Surface {
                        PermissionRequestScreen(
                            onRequestPermission = { requestOverlayPermission() }
                        )
                    }
                }
            }
        }
    }

    private fun hasScreenCapturePermission(): Boolean {
        val prefs = getSharedPreferences("capture_prefs", MODE_PRIVATE)
        return prefs.getBoolean("capture_granted", false)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun checkDisclaimerAndProceed() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val disclaimerAccepted = prefs.getBoolean("disclaimer_accepted", false)

        if (disclaimerAccepted) {
            startServiceAndFinish()
        } else {
            setContent {
                MaterialTheme {
                    Surface {
                        DisclaimerDialog(
                            onAccept = {
                                prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                                startServiceAndFinish()
                            },
                            onDecline = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun startServiceAndFinish() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        finish()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}

@Composable
fun DisclaimerDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable */ },
        title = {
            Text("Disclaimer", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "This app is not affiliated with or endorsed by Moonton. " +
                "Mobile Legends: Bang Bang is a trademark of Moonton.\n\n" +
                "All hero data comes from publicly available community APIs. " +
                "This app does not modify or interact with the game in any way.",
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("I Understand")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Exit")
            }
        }
    )
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "MLBB Draft Assistant", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Overlay permission is required.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
    }
}

@Composable
fun PermissionDeniedScreen(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    val openDialog = remember { mutableStateOf(true) }
    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Permission Denied") },
            text = {
                Text(
                    "Without overlay permission, the draft assistant cannot work.\n\n" +
                    "Enable it manually:\n" +
                    "Settings → Apps → MLBB Draft Assistant → Display over other apps."
                )
            },
            confirmButton = {
                TextButton(onClick = { openDialog.value = false; onOpenSettings() }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { openDialog.value = false; onCancel() }) { Text("Exit") }
            }
        )
    }
}