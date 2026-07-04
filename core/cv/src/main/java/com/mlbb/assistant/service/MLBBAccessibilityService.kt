package com.mlbb.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.mlbb.assistant.presentation.overlay.OverlayService

/**
 * Watches for MLBB coming to the foreground via window-state change events.
 * When detected, starts [OverlayService] (floating bubble).
 * When MLBB loses focus, signals the overlay to idle.
 *
 * ## What this service does NOT do
 *
 * This service performs **only window/package focus tracking** — it does NOT:
 * - Capture the screen or read pixel data.
 * - Perform any portrait / hero image detection.
 * - Interact with the MLBB UI in any way.
 *
 * Screen capture and all computer-vision work (ban/pick slot detection,
 * portrait matching) is owned exclusively by [OverlayCaptureCoordinator],
 * which requires a separate MediaProjection permission granted by the user.
 * These are two distinct permission flows (ideas.md §5).
 */
class MLBBAccessibilityService : AccessibilityService() {

    companion object {
        private const val MLBB_PACKAGE     = "com.mobile.legends"
        private const val MLBB_PACKAGE_ALT = "com.mobilelegends.mi"
        @Volatile private var isMLBBForeground = false
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes  = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags        = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val isMLBB = pkg == MLBB_PACKAGE || pkg == MLBB_PACKAGE_ALT

        when {
            isMLBB && !isMLBBForeground -> {
                isMLBBForeground = true
                OverlayService.start(applicationContext)
            }
            !isMLBB && isMLBBForeground && pkg != packageName -> {
                isMLBBForeground = false
                // Keep overlay alive but minimize bubble (handled inside OverlayService)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isMLBBForeground = false
    }
}
