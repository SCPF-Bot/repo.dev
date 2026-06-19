package com.mlbb.assistant.presentation.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mlbb.assistant.presentation.theme.MLBBAssistantTheme
import timber.log.Timber

/**
 * Transparent trampoline activity that requests the
 * SYSTEM_ALERT_WINDOW (draw-over-apps) permission and
 * then starts the [OverlayService] once granted.
 *
 * Uses [ActivityResultContracts.StartActivityForResult] so the permission
 * check happens on the result callback rather than in [onResume], avoiding
 * the double-trigger that the deprecated [onActivityResult] pattern suffered.
 */
class OverlayPermissionActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Timber.i("OverlayPermissionActivity: permission granted — starting service")
            OverlayService.start(this)
        } else {
            Timber.w("OverlayPermissionActivity: permission denied")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
            finish()
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        permissionLauncher.launch(intent)
    }
}
