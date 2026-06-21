package com.mlbb.assistant.utils

/**
 * Application-wide string and integer constants.
 *
 * Centralising these prevents magic literals from being scattered across
 * service and notification code, and makes them easy to find and update.
 */
object AppConstants {
    const val OVERLAY_NOTIFICATION_CHANNEL_ID = "draft_overlay_channel"
    const val OVERLAY_NOTIFICATION_ID         = 1001
}
