package com.mlbb.assistant.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mlbb.assistant.data.local.preferences.WizardPreference
import com.mlbb.assistant.presentation.herodetail.HeroDetailScreen
import com.mlbb.assistant.presentation.herolist.HeroListScreen
import com.mlbb.assistant.presentation.herolist.HeroListViewModel
import com.mlbb.assistant.presentation.heropool.HeroPoolScreen
import com.mlbb.assistant.presentation.history.DraftHistoryScreen
import com.mlbb.assistant.presentation.history.DraftReplayScreen
import com.mlbb.assistant.presentation.home.HomeScreen
import com.mlbb.assistant.presentation.log.LogScreen
import com.mlbb.assistant.presentation.metaboard.MetaBoardScreen
import com.mlbb.assistant.presentation.settings.SettingsScreen
import com.mlbb.assistant.presentation.welcome.PermissionWizardScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController:    NavHostController,
    startAtWizard:    Boolean,
    onStartOverlay:   () -> Unit,
    onRequestCapture: () -> Unit,
    modifier:         Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = if (startAtWizard) AppRoute.Wizard.route else AppRoute.Home.route,
        modifier         = modifier
    ) {
        composable(AppRoute.Wizard.route) {
            val context = LocalContext.current
            val scope   = rememberCoroutineScope()

            PermissionWizardScreen(
                onComplete = {
                    scope.launch {
                        WizardPreference.setDone(context, done = true)
                    }
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
            val state by vm.state.collectAsStateWithLifecycle()
            val heroMap = state.heroes.associateBy { it.id }
            val hero    = heroMap[heroId]
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
            val state by vm.state.collectAsStateWithLifecycle()
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
                onBack         = { navController.popBackStack() },
                onReplayClick  = { sessionId ->
                    navController.navigate(AppRoute.DraftReplay.create(sessionId))
                }
            )
        }

        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onBack        = { navController.popBackStack() },
                onOpenHeroPool = { navController.navigate(AppRoute.HeroPool.route) }
            )
        }

        composable(AppRoute.CrashLog.route) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.HeroPool.route) {
            HeroPoolScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = AppRoute.DraftReplay.route,
            arguments = listOf(navArgument(AppRoute.DraftReplay.ARG) { type = NavType.IntType })
        ) { backStack ->
            val sessionId = backStack.arguments?.getInt(AppRoute.DraftReplay.ARG) ?: return@composable
            DraftReplayScreen(
                sessionId = sessionId,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}
