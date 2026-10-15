package com.gymtracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymtracker.R
import com.gymtracker.ui.exercises.ExerciseDetailScreen
import com.gymtracker.ui.exercises.ExerciseEditScreen
import com.gymtracker.ui.exercises.ExerciseListScreen
import com.gymtracker.ui.home.HomeScreen
import com.gymtracker.ui.settings.SettingsScreen

/**
 * Nawigacja aplikacji: dolny pasek z sekcjami najwyższego poziomu
 * (Dziś / Ćwiczenia / Ustawienia) i ekrany pełnoekranowe (szczegóły, formularz),
 * na których paska nie pokazujemy.
 */
@Composable
fun GymTrackerNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentRoute != null && currentRoute in Routes.topLevel) {
                GymTrackerNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTopLevel(route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenExercises = { navController.navigateToTopLevel(Routes.EXERCISES) },
                    onOpenSettings = { navController.navigateToTopLevel(Routes.SETTINGS) },
                )
            }

            composable(Routes.EXERCISES) {
                ExerciseListScreen(
                    onExerciseClick = { exerciseId ->
                        navController.navigate(Routes.exerciseDetail(exerciseId))
                    },
                    onAddExercise = { navController.navigate(Routes.EXERCISE_NEW) },
                )
            }

            composable(Routes.EXERCISE_NEW) {
                ExerciseEditScreen(
                    onBack = { navController.popBackStack() },
                    // Po dodaniu pokazujemy od razu szczegóły nowego ćwiczenia.
                    onSaved = { exerciseId -> navController.navigateToExercise(exerciseId) },
                )
            }

            composable(
                route = Routes.EXERCISE_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.LongType }),
            ) {
                ExerciseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { exerciseId -> navController.navigate(Routes.exerciseEdit(exerciseId)) },
                )
            }

            composable(
                route = Routes.EXERCISE_EDIT,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.LongType }),
            ) {
                ExerciseEditScreen(
                    onBack = { navController.popBackStack() },
                    // Wracamy do szczegółów, które pokażą już zapisane dane.
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun GymTrackerNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        BottomDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(imageVector = destination.icon, contentDescription = null)
                },
                label = { Text(text = stringResource(destination.labelRes)) },
            )
        }
    }
}

/** Sekcje dostępne z dolnego paska. */
private enum class BottomDestination(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    TODAY(Routes.HOME, Icons.Filled.DateRange, R.string.nav_today),
    EXERCISES(Routes.EXERCISES, Icons.Filled.List, R.string.nav_exercises),
    SETTINGS(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
}

/** Przejście do sekcji najwyższego poziomu bez piętrzenia kopii w stosie. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Po zapisaniu nowego ćwiczenia czyścimy formularz ze stosu i pokazujemy szczegóły. */
private fun NavHostController.navigateToExercise(exerciseId: Long) {
    navigate(Routes.exerciseDetail(exerciseId)) {
        popUpTo(Routes.EXERCISES) { inclusive = false }
        launchSingleTop = true
    }
}
