package com.mlbb.assistant.presentation.navigation

sealed class AppRoute(val route: String) {
    data object Wizard    : AppRoute("wizard")
    data object Home      : AppRoute("home")
    data object HeroList  : AppRoute("hero_list")
    data object MetaBoard : AppRoute("meta_board")
    data object History   : AppRoute("history")
    data object Settings  : AppRoute("settings")

    data object HeroDetail : AppRoute("hero_detail/{heroId}") {
        const val ARG = "heroId"
        fun create(heroId: Int) = "hero_detail/$heroId"
    }
}

val TOP_LEVEL_ROUTES = setOf(
    AppRoute.Home.route,
    AppRoute.HeroList.route,
    AppRoute.MetaBoard.route,
    AppRoute.History.route,
    AppRoute.Settings.route,
)
