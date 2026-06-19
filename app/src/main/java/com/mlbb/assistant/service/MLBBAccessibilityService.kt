package com.mlbb.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * Accessibility service that detects when MLBB comes to the foreground
 * so the overlay can activate automatically without the user manually
 * pressing "Start Draft".
 *
 * Permission: declared in AndroidManifest.xml with
 *   android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
 * and configured via res/xml/accessibility_service_config.xml.
 *
 * Note: This service only monitors window state changes (the lowest-overhead
 * event type). It does NOT read UI content, traverse the window hierarchy, or
 * capture any screen content — all screen capture is handled separately via
 * MediaProjection in OverlayService.
 */
class MLBBAccessibilityService : AccessibilityService() {

    companion object {
        private const val MLBB_PACKAGE = "com.mobile.legends"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == MLBB_PACKAGE) {
            Timber.d("MLBBAccessibilityService: MLBB foregrounded — broadcasting")
            sendBroadcast(Intent("com.mlbb.assistant.ACTION_MLBB_FOREGROUNDED"))
        }
    }

    override fun onInterrupt() {
        Timber.w("MLBBAccessibilityService: interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("MLBBAccessibilityService: connected")
    }
}
