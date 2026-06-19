package com.mlbb.assistant.presentation.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mlbb.assistant.presentation.shell.AppShell
import com.mlbb.assistant.presentation.theme.MLBBAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point for the MLBB Draft Assistant.
 *
 * Responsibilities deliberately kept minimal:
 *  - Enable edge-to-edge display
 *  - Provide the Hilt component tree (@AndroidEntryPoint)
 *  - Set the Compose content root ([AppShell])
 *
 * All navigation, theme, and lifecycle logic lives in [AppShell] and
 * the ViewModels it drives — no business logic belongs here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MLBBAssistantTheme {
                AppShell()
            }
        }
    }
}
