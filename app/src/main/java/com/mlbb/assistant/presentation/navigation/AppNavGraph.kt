package com.mlbb.assistant.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Navigation graph for the MLBB Draft Assistant.
 *
 * Routes:
 *  - "onboarding"    — first-run wizard (shown when [wizardCompleted] == false)
 *  - "home"          — main hub: meta overview + start draft
 *  - "hero_list"     — browsable hero roster with search and role filters
 *  - "draft"         — live draft assistant (ban/pick phase guidance)
 *  - "history"       — completed session history with follow-rate analytics
 *  - "settings"      — score weight sliders + overlay configuration
 *
 * Removed: the previous implementation read SharedPreferences directly inside
 * this composable to determine the start destination. The flag is now passed
 * in as [wizardCompleted] from [AppShell], which sources it from DataStore.
 */
@Composable
fun AppNavGraph(wizardCompleted: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (wizardCompleted) "home" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {

        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToDraft    = { navController.navigate("draft") },
                onNavigateToHeroes   = { navController.navigate("hero_list") },
                onNavigateToHistory  = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("hero_list") {
            HeroListScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("draft") {
            DraftScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
