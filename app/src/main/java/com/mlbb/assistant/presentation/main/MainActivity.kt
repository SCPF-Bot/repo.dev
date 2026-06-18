package com.mlbb.assistant.presentation.main

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.presentation.overlay.OverlayService
import com.mlbb.assistant.presentation.shell.AppShell
import com.mlbb.assistant.service.VoiceAlertService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var voiceAlertService: VoiceAlertService

    /**
     * Launched after the user grants the screen-capture permission dialog.
     * We pass the result directly to OverlayService so it can set up
     * MediaProjection and start the autonomous capture loop — without
     * keeping a local ScreenCaptureManager here.
     */
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            OverlayService.startWithProjection(this, result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MLBBAssistantTheme {
                AppShell(
                    onStartOverlay   = { startOverlay() },
                    onRequestCapture = { requestScreenCapture() }
                )
            }
        }
    }

    override fun onDestroy() {
        voiceAlertService.shutdown()
        super.onDestroy()
    }

    /**
     * Called when the user taps "Start Draft".
     *
     * Steps:
     * 1. Verify overlay permission — if missing, open the system settings page.
     * 2. Start OverlayService immediately (bubble appears).
     * 3. Request screen-capture permission so autonomous detection can start.
     *    The result flows back through [projectionLauncher] → OverlayService.
     */
    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            OverlayService.start(this) // service will show a prompt via OverlayPermissionActivity
            return
        }
        OverlayService.start(this)
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
