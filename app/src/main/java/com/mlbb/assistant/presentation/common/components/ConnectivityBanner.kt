package com.mlbb.assistant.presentation.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlbb.assistant.presentation.common.theme.ErrorRed
import com.mlbb.assistant.presentation.common.theme.TextPrimary

/**
 * A slim animated banner that appears at the top of the screen when the device
 * is offline, and disappears when connectivity is restored.
 *
 * Accessibility:
 * - The Row carries [liveRegion = LiveRegionMode.Polite] so TalkBack announces
 *   the change in connectivity state without interrupting ongoing speech.
 * - The WifiOff icon has a null contentDescription because the parent text
 *   already fully describes the state.
 *
 * Usage — place at the top of your scaffold body:
 * ```
 * Scaffold { padding ->
 *     Column(Modifier.padding(padding)) {
 *         ConnectivityBanner(isOffline = !isConnected)
 *         // ... rest of content
 *     }
 * }
 * ```
 */
@Composable
fun ConnectivityBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOffline,
        enter   = expandVertically(),
        exit    = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(ErrorRed)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                imageVector        = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint               = TextPrimary,
                modifier           = Modifier
                    .size(14.dp)
                    .padding(end = 4.dp)
            )
            Text(
                text       = "No internet connection — showing cached data",
                color      = TextPrimary,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
