package com.mlbb.assistant.presentation.common.utils

sealed class Screen(val route: String) {
    object HeroList : Screen("hero_list")
    object Draft : Screen("draft")
    object Settings : Screen("settings")
}