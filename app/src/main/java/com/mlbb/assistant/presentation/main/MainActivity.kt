package com.mlbb.assistant.presentation.main

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.presentation.shell.AppShell
import com.mlbb.assistant.service.ScreenCaptureManager
import com.mlbb.assistant.service.VoiceAlertService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Pass 3: VoiceAlertService holds a TextToSpeech instance; shutdown() releases the TTS engine.
    @Inject lateinit var voiceAlertService: VoiceAlertService

    private lateinit var screenCaptureManager: ScreenCaptureManager
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            screenCaptureManager.startCapture(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        screenCaptureManager = ScreenCaptureManager(this)

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
        screenCaptureManager.stopCapture()
        voiceAlertService.shutdown()
        super.onDestroy()
    }

    private fun startOverlay() {
        val canDraw = android.provider.Settings.canDrawOverlays(this)
        if (canDraw) {
            com.mlbb.assistant.presentation.overlay.OverlayService.start(this)
        }
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
