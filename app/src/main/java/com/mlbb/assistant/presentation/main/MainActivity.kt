// File: app/src/main/java/com/mlbb/assistant/presentation/main/MainActivity.kt
package com.mlbb.assistant.presentation.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.presentation.draft.DraftScreen
import com.mlbb.assistant.presentation.herolist.HeroListScreen
import com.mlbb.assistant.presentation.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MLBBAssistantTheme {
                Surface {
                    MLBBAppNav()
                }
            }
        }
    }

    @Composable
    fun MLBBAppNav() {
        val navController = rememberNavController()
        Scaffold { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "hero_list",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("hero_list") {
                    HeroListScreen(navController)
                }
                composable("draft") {
                    DraftScreen(navController)
                }
                composable("settings") {
                    SettingsScreen(navController)
                }
            }
        }
    }
}