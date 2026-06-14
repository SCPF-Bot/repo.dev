package com.mlbb.assistant.domain.usecase

import android.content.Context
import android.provider.Settings
import com.mlbb.assistant.presentation.overlay.OverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ToggleOverlayUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(start: Boolean) {
        if (!Settings.canDrawOverlays(context)) return
        if (start) OverlayService.start(context) else OverlayService.stop(context)
    }
}
