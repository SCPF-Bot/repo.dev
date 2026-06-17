package com.mlbb.assistant.presentation.navigation

sealed class AppRoute(val route: String) {
    object Wizard    : AppRoute("wizard")
    object Home      : AppRoute("home")
    object HeroList  : AppRoute("hero_list")
    object MetaBoard : AppRoute("meta_board")
    object History   : AppRoute("history")
    object Settings  : AppRoute("settings")

    object HeroDetail : AppRoute("hero_detail/{heroId}") {
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
