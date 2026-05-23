package com.example.mlbbdraftassistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
                startOverlayService()
                finish()
            } else {
                // Permission still denied → show a dialog asking to retry
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            finish()
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

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
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
        Text(
            text = "MLBB Draft Assistant",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Overlay permission is required for the draft assistant to appear during the game.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun PermissionDeniedScreen(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    val openDialog = remember { mutableStateOf(true) }

    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { /* non-dismissable */ },
            title = { Text("Permission Denied") },
            text = {
                Text(
                    "Without overlay permission, the draft assistant cannot work.\n\n" +
                    "Please enable it manually in:\nSettings → Apps → MLBB Draft Assistant → Display over other apps."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openDialog.value = false
                    onOpenSettings()
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    openDialog.value = false
                    onCancel()
                }) {
                    Text("Exit")
                }
            }
        )
    }
}