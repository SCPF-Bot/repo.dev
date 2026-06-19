package com.mlbb.assistant.presentation.shell

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mlbb.assistant.presentation.navigation.AppNavGraph
import com.mlbb.assistant.presentation.navigation.AppRoute
import com.mlbb.assistant.presentation.navigation.TOP_LEVEL_ROUTES

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

private val NAV_ITEMS = listOf(
    BottomNavItem(AppRoute.Home.route,      Icons.Rounded.Home,        "Home"),
    BottomNavItem(AppRoute.HeroList.route,  Icons.Rounded.Person,      "Heroes"),
    BottomNavItem(AppRoute.MetaBoard.route, Icons.Rounded.Leaderboard, "Meta"),
    BottomNavItem(AppRoute.History.route,   Icons.Rounded.History,     "History"),
    BottomNavItem(AppRoute.Settings.route,  Icons.Rounded.Settings,    "Settings"),
)

@Composable
fun AppShell(
    onStartOverlay: () -> Unit,
    onRequestCapture: () -> Unit
) {
    val context       = LocalContext.current
    val startAtWizard = remember {
        !context.getSharedPreferences("mlbb_prefs", Context.MODE_PRIVATE)
            .getBoolean("wizard_done", false)
    }

    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter   = slideInVertically { it },
                exit    = slideOutVertically { it }
            ) {
                NavigationBar {
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavGraph(
            navController    = navController,
            startAtWizard    = startAtWizard,
            onStartOverlay   = onStartOverlay,
            onRequestCapture = onRequestCapture,
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
