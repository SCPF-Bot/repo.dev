package com.mlbb.assistant.presentation.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme

class OverlayPermissionActivity : ComponentActivity() {

    // Receives result when user returns from the system overlay settings screen
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returned — check if permission was granted
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
        // else: stay on screen so user can try again or dismiss
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If permission already granted on launch, go straight to service
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            return
        }
        setContent {
            MLBBAssistantTheme {
                OverlayPermissionScreen(onRequestPermission = ::requestOverlayPermission)
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}

@Composable
fun OverlayPermissionScreen(onRequestPermission: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Overlay Permission Required")
            Text("To show draft suggestions on top of MLBB, grant overlay permission.")
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Grant Permission")
            }
        }
    }
}
