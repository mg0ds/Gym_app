package com.gymtracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gymtracker.ui.home.HomeScreen
import com.gymtracker.ui.settings.SettingsScreen

/** Trasy nawigacji. Kolejne ekrany dochodzą tu wraz z katalogiem ćwiczeń i planami. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun GymTrackerNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
