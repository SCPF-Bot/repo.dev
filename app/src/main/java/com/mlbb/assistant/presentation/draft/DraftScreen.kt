package com.mlbb.assistant.presentation.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mlbb.assistant.presentation.common.components.LoadingSpinner
import com.mlbb.assistant.presentation.common.components.MLBBTextField
import com.mlbb.assistant.presentation.draft.components.HeroChip
import com.mlbb.assistant.presentation.draft.components.SuggestionCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DraftScreen(viewModel: DraftViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    // rememberSaveable survives configuration changes
    var allyInput by rememberSaveable { mutableStateOf("") }
    var enemyInput by rememberSaveable { mutableStateOf("") }
    var banInput by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadHeroes() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Draft Assistant") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MLBBTextField(
                    value = allyInput,
                    onValueChange = { allyInput = it },
                    label = "Add Ally Hero (name)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (allyInput.isNotBlank()) {
                            viewModel.addAlly(allyInput)
                            allyInput = ""
                        }
                    },
                    enabled = allyInput.isNotBlank()
                ) { Text("Add Ally") }
            }

            item {
                MLBBTextField(
                    value = enemyInput,
                    onValueChange = { enemyInput = it },
                    label = "Add Enemy Hero (name)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (enemyInput.isNotBlank()) {
                            viewModel.addEnemy(enemyInput)
                            enemyInput = ""
                        }
                    },
                    enabled = enemyInput.isNotBlank()
                ) { Text("Add Enemy") }
            }

            item {
                MLBBTextField(
                    value = banInput,
                    onValueChange = { banInput = it },
                    label = "Ban Hero (name)",
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (banInput.isNotBlank()) {
                            viewModel.addBan(banInput)
                            banInput = ""
                        }
                    },
                    enabled = banInput.isNotBlank()
                ) { Text("Add Ban") }
            }

            item {
                Text("Allies:", modifier = Modifier.padding(top = 8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.allies.forEach { ally ->
                        HeroChip(hero = ally, onRemove = { viewModel.removeAlly(ally) })
                    }
                }
            }

            item {
                Text("Enemies:")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.enemies.forEach { enemy ->
                        HeroChip(hero = enemy, onRemove = { viewModel.removeEnemy(enemy) })
                    }
                }
            }

            item {
                Text("Bans:")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                items(
                    items = state.suggestions.take(5),
                    key = { (hero, _) -> hero.id }
                ) { (hero, score) ->
                    SuggestionCard(hero = hero, score = score)
                }
            }

            if (state.error != null) {
                item { Text("Error: ${state.error}", color = Color.Red) }
            }
        }
    }
}
