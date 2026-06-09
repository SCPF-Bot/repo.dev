package com.mlbb.assistant.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.mlbb.assistant.presentation.overlay.OverlayService
import javax.inject.Inject

class ToggleOverlayUseCase @Inject constructor() {

    fun isOverlayEnabled(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun startOverlay(context: Context) {
        if (isOverlayEnabled(context)) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }

    fun stopOverlay(context: Context) {
        context.stopService(Intent(context, OverlayService::class.java))
    }

    fun getOverlayPermissionIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.mlbb.assistant"))
    }
}