// File: app/src/main/java/com/mlbb/assistant/presentation/overlay/OverlayPermissionActivity.kt
package com.mlbb.assistant.presentation.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MLBBAssistantTheme {
                OverlayPermissionScreen(
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!Settings.canDrawOverlays(this)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                            } else {
                                startOverlayService()
                            }
                        } else {
                            startOverlayService()
                        }
                    }
                )
            }
        }
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}

@Composable
fun OverlayPermissionScreen(onRequestPermission: () -> Unit) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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