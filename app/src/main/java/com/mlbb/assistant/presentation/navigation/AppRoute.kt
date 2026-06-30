package com.mlbb.assistant.presentation.navigation

sealed class AppRoute(val route: String) {
    data object Wizard    : AppRoute("wizard")
    data object Home      : AppRoute("home")
    data object HeroList  : AppRoute("hero_list")
    data object MetaBoard : AppRoute("meta_board")
    data object History   : AppRoute("history")
    data object Settings  : AppRoute("settings")
    data object HeroPool  : AppRoute("hero_pool")

    data object HeroDetail : AppRoute("hero_detail/{heroId}") {
        const val ARG = "heroId"
        fun create(heroId: Int) = "hero_detail/$heroId"
    }

    data object DraftReplay : AppRoute("draft_replay/{sessionId}") {
        const val ARG = "sessionId"
        fun create(sessionId: Int) = "draft_replay/$sessionId"
    }
}

val TOP_LEVEL_ROUTES = setOf(
    AppRoute.Home.route,
    AppRoute.HeroList.route,
    AppRoute.MetaBoard.route,
    AppRoute.History.route,
    AppRoute.Settings.route,
)
