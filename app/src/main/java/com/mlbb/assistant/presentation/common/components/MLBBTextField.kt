package com.mlbb.assistant.presentation.common.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Branded outlined text field.
 *
 * UX fixes applied:
 * - Added [isError] so callers can surface validation feedback inline
 *   (Nielsen #9 — Error Prevention; Nielsen #10 — Error Recovery).
 * - Added [enabled] pass-through for disabled/read-only states.
 * - Added [contentDescription] for accessibility — TalkBack will announce
 *   the semantic label rather than the raw placeholder string.
 */
@Composable
fun MLBBTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    contentDescription: String = label
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = modifier.semantics { this.contentDescription = contentDescription },
        singleLine    = singleLine,
        enabled       = enabled,
        isError       = isError
    )
}
