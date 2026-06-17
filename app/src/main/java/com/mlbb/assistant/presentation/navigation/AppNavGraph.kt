package com.mlbb.assistant.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mlbb.assistant.presentation.herodetail.HeroDetailScreen
import com.mlbb.assistant.presentation.herolist.HeroListScreen
import com.mlbb.assistant.presentation.herolist.HeroListViewModel
import com.mlbb.assistant.presentation.history.DraftHistoryScreen
import com.mlbb.assistant.presentation.home.HomeScreen
import com.mlbb.assistant.presentation.metaboard.MetaBoardScreen
import com.mlbb.assistant.presentation.settings.SettingsScreen
import com.mlbb.assistant.presentation.welcome.PermissionWizardScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    onStartOverlay: () -> Unit,
    onRequestCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = AppRoute.Home.route,
        modifier         = modifier
    ) {
        composable(AppRoute.Wizard.route) {
            PermissionWizardScreen(
                onComplete = {
                    navController.navigate(AppRoute.Home.route) {
                        popUpTo(AppRoute.Wizard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoute.Home.route) {
            HomeScreen(
                onStartDraft   = onStartOverlay,
                onOpenExplorer = { navController.navigate(AppRoute.HeroList.route) },
                onOpenMeta     = { navController.navigate(AppRoute.MetaBoard.route) },
                onOpenHistory  = { navController.navigate(AppRoute.History.route) },
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
            )
        }

        composable(AppRoute.HeroList.route) {
            HeroListScreen(
                onHeroClick = { hero ->
                    navController.navigate(AppRoute.HeroDetail.create(hero.id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = AppRoute.HeroDetail.route,
            arguments = listOf(navArgument(AppRoute.HeroDetail.ARG) { type = NavType.IntType })
        ) { backStack ->
            val heroId = backStack.arguments?.getInt(AppRoute.HeroDetail.ARG) ?: return@composable
            val vm: HeroListViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            val heroMap = remember(state.heroes) { state.heroes.associateBy { it.id } }
            val hero = heroMap[heroId]
            if (hero != null) {
                HeroDetailScreen(
                    hero          = hero,
                    relatedHeroes = heroMap,
                    onBack        = { navController.popBackStack() }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        composable(AppRoute.MetaBoard.route) {
            val vm: HeroListViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            MetaBoardScreen(
                heroes      = state.heroes,
                onHeroClick = { hero ->
                    navController.navigate(AppRoute.HeroDetail.create(hero.id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.History.route) {
            DraftHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
