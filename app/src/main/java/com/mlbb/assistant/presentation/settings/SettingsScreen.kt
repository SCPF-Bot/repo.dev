package com.mlbb.assistant.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var metaWeight by remember { mutableStateOf(state.metaWeight) }
    var counterWeight by remember { mutableStateOf(state.counterWeight) }
    var synergyWeight by remember { mutableStateOf(state.synergyWeight) }

    LaunchedEffect(state.metaWeight, state.counterWeight, state.synergyWeight) {
        metaWeight = state.metaWeight
        counterWeight = state.counterWeight
        synergyWeight = state.synergyWeight
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { paddingValues ->
        // verticalScroll prevents content being clipped on small screens
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // steps = 9 → 11 positions: 0.0, 0.1, … 1.0
            Text("Meta Weight: ${String.format(Locale.US, "%.2f", metaWeight)}")
            Slider(
                value = metaWeight.toFloat(),
                onValueChange = { metaWeight = it.toDouble() },
                valueRange = 0f..1f,
                steps = 9
            )

            Text("Counter Weight: ${String.format(Locale.US, "%.2f", counterWeight)}")
            Slider(
                value = counterWeight.toFloat(),
                onValueChange = { counterWeight = it.toDouble() },
                valueRange = 0f..1f,
                steps = 9
            )

            Text("Synergy Weight: ${String.format(Locale.US, "%.2f", synergyWeight)}")
            Slider(
                value = synergyWeight.toFloat(),
                onValueChange = { synergyWeight = it.toDouble() },
                valueRange = 0f..1f,
                steps = 9
            )

            Button(
                onClick = { viewModel.saveWeights(metaWeight, counterWeight, synergyWeight) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Save")
            }

            if (state.isSaved) {
                Text("Settings saved!", color = Color.Green)
            }
        }
    }
}
