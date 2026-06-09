// File: app/src/main/java/com/mlbb/assistant/presentation/draft/components/HeroChip.kt
package com.mlbb.assistant.presentation.draft.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlbb.assistant.domain.model.Hero

@Composable
fun HeroChip(hero: Hero, onRemove: () -> Unit) {
    AssistChip(
        onClick = onRemove,
        label = { Text(hero.name) },
        modifier = Modifier.padding(4.dp)
    )
}