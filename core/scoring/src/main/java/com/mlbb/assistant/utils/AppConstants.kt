package com.mlbb.assistant.utils

/**
 * Application-wide string and integer constants.
 *
 * Centralising these prevents magic literals from being scattered across
 * service and notification code, and makes them easy to find and update.
 *
 * P2-02 fix: removed dead constant `OVERLAY_NOTIFICATION_CHANNEL_ID`.
 * That constant held `"draft_overlay_channel"` but was never referenced;
 * `OverlayService` defines its own private `NOTIF_CHANNEL = "overlay_channel"`
 * with a different string value, making the constant here both dead and
 * misleading about which ID is authoritative. The live channel ID now lives
 * exclusively in `OverlayService`.
 */
object AppConstants {
    const val OVERLAY_NOTIFICATION_ID = 1001
}
