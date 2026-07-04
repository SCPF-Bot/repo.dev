package com.mlbb.assistant.presentation.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Full-size centred loading indicator.
 *
 * UX fix applied:
 * - Added [contentDescription] (default "Loading") so TalkBack announces the
 *   loading state to screen-reader users (WCAG 4.1.2 Name, Role, Value).
 *   Without this, the spinner was invisible to assistive technology.
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    contentDescription: String = "Loading"
) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { this.contentDescription = contentDescription }
        )
    }
}
