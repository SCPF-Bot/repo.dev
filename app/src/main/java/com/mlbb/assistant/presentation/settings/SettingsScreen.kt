// File: app/src/main/java/com/mlbb/assistant/presentation/settings/SettingsScreen.kt
package com.mlbb.assistant.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var metaWeight by remember { mutableStateOf(state.metaWeight) }
    var counterWeight by remember { mutableStateOf(state.counterWeight) }
    var synergyWeight by remember { mutableStateOf(state.synergyWeight) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            Text("Meta Weight: ${String.format("%.2f", metaWeight)}")
            Slider(
                value = metaWeight.toFloat(),
                onValueChange = { metaWeight = it.toDouble() },
                valueRange = 0f..1f,
                steps = 10
            )
            Text("Counter Weight: ${String.format("%.2f", counterWeight)}")
            Slider(
                value = counterWeight.toFloat(),
                onValueChange = { counterWeight = it.toDouble() },
                valueRange = 0f..1f,
                steps = 10
            )
            Text("Synergy Weight: ${String.format("%.2f", synergyWeight)}")
            Slider(
                value = synergyWeight.toFloat(),
                onValueChange = { synergyWeight = it.toDouble() },
                valueRange = 0f..1f,
                steps = 10
            )
            Button(
                onClick = {
                    viewModel.saveWeights(metaWeight, counterWeight, synergyWeight)
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Save")
            }
            if (state.isSaved) {
                Text("Settings saved!", color = androidx.compose.ui.graphics.Color.Green)
            }
        }
    }
}