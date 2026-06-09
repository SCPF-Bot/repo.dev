package com.mlbb.assistant.presentation.draft.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlbb.assistant.domain.model.Hero

@Composable
fun SuggestionCard(hero: Hero, score: Double) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = hero.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "Score: ${String.format("%.2f", score)}")
            Text(text = "Role: ${hero.role}")
            Text(text = "Win Rate: ${String.format("%.1f", hero.winRate * 100)}%")
        }
    }
}