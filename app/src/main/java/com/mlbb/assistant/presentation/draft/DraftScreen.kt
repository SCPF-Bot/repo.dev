// File: app/src/main/java/com/mlbb/assistant/presentation/draft/DraftScreen.kt
package com.mlbb.assistant.presentation.draft

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mlbb.assistant.presentation.common.components.LoadingSpinner
import com.mlbb.assistant.presentation.common.components.MLBBTextField
import com.mlbb.assistant.presentation.draft.components.HeroChip
import com.mlbb.assistant.presentation.draft.components.SuggestionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftScreen(
    navController: NavController,
    viewModel: DraftViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var allyInput by remember { mutableStateOf("") }
    var enemyInput by remember { mutableStateOf("") }
    var banInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadHeroes()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Draft Assistant") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            item {
                MLBBTextField(
                    value = allyInput,
                    onValueChange = { allyInput = it },
                    label = "Add Ally Hero (name)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    viewModel.addAlly(allyInput)
                    allyInput = ""
                }) { Text("Add Ally") }
            }
            item {
                MLBBTextField(
                    value = enemyInput,
                    onValueChange = { enemyInput = it },
                    label = "Add Enemy Hero (name)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    viewModel.addEnemy(enemyInput)
                    enemyInput = ""
                }) { Text("Add Enemy") }
            }
            item {
                MLBBTextField(
                    value = banInput,
                    onValueChange = { banInput = it },
                    label = "Ban Hero (name)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    viewModel.addBan(banInput)
                    banInput = ""
                }) { Text("Add Ban") }
            }
            item {
                Text("Allies:", Modifier.padding(top = 8.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    state.allies.forEach { ally ->
                        HeroChip(hero = ally, onRemove = { viewModel.removeAlly(ally) })
                    }
                }
            }
            item {
                Text("Enemies:")
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    state.enemies.forEach { enemy ->
                        HeroChip(hero = enemy, onRemove = { viewModel.removeEnemy(enemy) })
                    }
                }
            }
            item {
                Text("Bans:")
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    state.bans.forEach { ban ->
                        HeroChip(hero = ban, onRemove = { viewModel.removeBan(ban) })
                    }
                }
            }
            if (state.isLoading) {
                item { LoadingSpinner() }
            } else {
                item { Text("Top Suggestions:", modifier = Modifier.padding(top = 16.dp)) }
                items(state.suggestions.take(5)) { (hero, score) ->
                    SuggestionCard(hero = hero, score = score)
                }
            }
            if (state.error != null) {
                item { Text("Error: ${state.error}", color = androidx.compose.ui.graphics.Color.Red) }
            }
        }
    }
}