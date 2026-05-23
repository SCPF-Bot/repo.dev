package com.example.mlbbdraftassistant

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                if (hasScreenCapturePermission()) {
                    startServiceAndFinish()
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
                // Forward the MediaProjection intent to the service
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SET_MEDIA_PROJECTION
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(serviceIntent)
                finish()
            } else {
                Toast.makeText(this, "Screen capture permission is required for auto-detection", Toast.LENGTH_LONG).show()
                startServiceAndFinish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            if (hasScreenCapturePermission()) {
                startServiceAndFinish()
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
        // MediaProjection permission is temporary; we store a flag.
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
            text = { Text("Without overlay permission, the draft assistant cannot work.\n\nEnable it manually:\nSettings → Apps → MLBB Draft Assistant → Display over other apps.") },
            confirmButton = {
                TextButton(onClick = { openDialog.value = false; onOpenSettings() }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { openDialog.value = false; onCancel() }) { Text("Exit") }
            }
        )
    }
}
