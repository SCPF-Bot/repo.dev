package com.mlbb.assistant.presentation.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.presentation.common.utils.Screen
import com.mlbb.assistant.presentation.draft.DraftScreen
import com.mlbb.assistant.presentation.herolist.HeroListScreen
import com.mlbb.assistant.presentation.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MLBBAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MLBBAppNav()
                }
            }
        }
    }
}

@Composable
fun MLBBAppNav(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.HeroList.route
    ) {
        composable(Screen.HeroList.route) { HeroListScreen() }
        composable(Screen.Draft.route) { DraftScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
