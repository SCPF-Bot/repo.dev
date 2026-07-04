package com.mlbb.assistant.presentation.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mlbb.assistant.presentation.common.theme.MLBBGold

@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick  = onBack,
        modifier = modifier.semantics { contentDescription = "Navigate back" }
    ) {
        Icon(
            imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint               = MLBBGold.copy(alpha = 0.90f)
        )
    }
}
