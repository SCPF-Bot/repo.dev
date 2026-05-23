package com.example.mlbbdraftassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GameAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_DRAFT_ENTERED = "com.example.mlbbdraftassistant.DRAFT_ENTERED"
        const val ACTION_DRAFT_EXITED = "com.example.mlbbdraftassistant.DRAFT_EXITED"
        const val MLBB_PACKAGE = "com.mobile.legends"

        // Known activity names for draft/pick screen (may need verification)
        private val DRAFT_ACTIVITIES = setOf(
            "com.mobile.legends.heroselect.HeroSelectActivity",
            "com.mobile.legends.draft.DraftActivity",
            "com.mobile.legends.heroselect.HeroSelectActivityV2"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (packageName != MLBB_PACKAGE) return

        val isDraft = DRAFT_ACTIVITIES.any { className.startsWith(it) }

        if (isDraft) {
            sendBroadcast(Intent(ACTION_DRAFT_ENTERED))
        } else {
            // Only send exit if we were previously in draft? For simplicity, always send exit when leaving any MLBB screen.
            sendBroadcast(Intent(ACTION_DRAFT_EXITED))
        }
    }

    override fun onInterrupt() {}
}
