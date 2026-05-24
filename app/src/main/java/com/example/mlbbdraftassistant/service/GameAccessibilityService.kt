package com.example.mlbbdraftassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GameAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_DRAFT_ENTERED = "com.example.mlbbdraftassistant.DRAFT_ENTERED"
        const val ACTION_DRAFT_EXITED  = "com.example.mlbbdraftassistant.DRAFT_EXITED"
        const val MLBB_PACKAGE = "com.mobile.legends"

        // Known activity names for draft/pick screen (may need verification)
        private val DRAFT_ACTIVITIES = setOf(
            "com.mobile.legends.heroselect.HeroSelectActivity",
            "com.mobile.legends.draft.DraftActivity",
            "com.mobile.legends.heroselect.HeroSelectActivityV2"
        )
    }

    /** Track whether we were previously in a draft so we don't spam exit events. */
    private var wasInDraft = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            // FIX: declare the package filter so the system only wakes us for MLBB
            packageNames = arrayOf(MLBB_PACKAGE)
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className   = event.className?.toString()  ?: return

        if (packageName != MLBB_PACKAGE) return

        val isNowInDraft = DRAFT_ACTIVITIES.any { className.startsWith(it) }

        // FIX: only broadcast when the state actually changes to avoid flooding
        // OverlayService with repeated ACTION_DRAFT_ENTERED / DRAFT_EXITED intents.
        if (isNowInDraft && !wasInDraft) {
            sendBroadcast(Intent(ACTION_DRAFT_ENTERED).apply {
                `package` = packageName  // FIX: explicit package makes broadcast non-implicit
            })
        } else if (!isNowInDraft && wasInDraft) {
            sendBroadcast(Intent(ACTION_DRAFT_EXITED).apply {
                `package` = packageName
            })
        }

        wasInDraft = isNowInDraft
    }

    override fun onInterrupt() {}
}
