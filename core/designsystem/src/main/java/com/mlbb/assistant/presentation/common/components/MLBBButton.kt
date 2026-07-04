package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val MIN_TOUCH_TARGET = 48.dp

/**
 * Primary branded button.
 *
 * UX fixes applied:
 * - Enforces a 48 dp minimum height (WCAG 2.5.5 Target Size — touch targets ≥ 44 dp).
 * - Exposes [contentDescription] so TalkBack can announce the action when the visible
 *   label text is ambiguous or an icon-only variant is needed in the future.
 */
@Composable
fun MLBBButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    Button(
        onClick  = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
            .semantics { this.contentDescription = contentDescription },
        enabled  = enabled
    ) {
        Text(text)
    }
}
