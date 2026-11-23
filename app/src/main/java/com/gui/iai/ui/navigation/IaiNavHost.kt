package com.gui.iai.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gui.iai.ui.calibration.CalibrationScreen
import com.gui.iai.ui.duel.DuelScreen
import com.gui.iai.ui.duel.DuelViewModel
import com.gui.iai.ui.menu.HomeScreen
import com.gui.iai.ui.menu.PathScreen
import com.gui.iai.ui.menu.RecordsScreen
import com.gui.iai.ui.menu.SettingsScreen
import com.gui.iai.ui.training.TrainingScreen
import com.gui.iai.ui.versus.VersusScreen

private object Routes {
    const val HOME = "home"
    const val PATH = "path"
    const val DUEL = "duel/{${DuelViewModel.ARG_OPPONENT}}"
    const val VERSUS = "versus"
    const val TRAINING = "training"
    const val RECORDS = "records"
    const val SETTINGS = "settings"
    const val CALIBRATE = "calibrate"

    fun duel(opponentId: String) = "duel/$opponentId"
}

@Composable
fun IaiNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(350)) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(350)) },
        popExitTransition = { fadeOut(tween(250)) }
    ) {
        composable(Routes.HOME) { entry ->
            HomeScreen(
                onDuel = { entry.ifResumed { navController.navigate(Routes.PATH) } },
                onVersus = { entry.ifResumed { navController.navigate(Routes.VERSUS) } },
                onTraining = { entry.ifResumed { navController.navigate(Routes.TRAINING) } },
                onRecords = { entry.ifResumed { navController.navigate(Routes.RECORDS) } },
                onSettings = { entry.ifResumed { navController.navigate(Routes.SETTINGS) } }
            )
        }

        composable(Routes.PATH) { entry ->
            PathScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onChallenge = { opponent ->
                    entry.ifResumed { navController.navigate(Routes.duel(opponent.id)) }
                }
            )
        }

        composable(
            route = Routes.DUEL,
            arguments = listOf(navArgument(DuelViewModel.ARG_OPPONENT) { type = NavType.StringType })
        ) { entry ->
            DuelScreen(
                onExit = { entry.ifResumed { navController.popBackStack() } },
                onOpenPath = {
                    entry.ifResumed {
                        navController.popBackStack(Routes.PATH, inclusive = false)
                    }
                }
            )
        }

        composable(Routes.VERSUS) { entry ->
            VersusScreen(onExit = { entry.ifResumed { navController.popBackStack() } })
        }

        composable(Routes.TRAINING) { entry ->
            TrainingScreen(onExit = { entry.ifResumed { navController.popBackStack() } })
        }

        composable(Routes.RECORDS) { entry ->
            RecordsScreen(onBack = { entry.ifResumed { navController.popBackStack() } })
        }

        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onCalibrate = { entry.ifResumed { navController.navigate(Routes.CALIBRATE) } }
            )
        }

        composable(Routes.CALIBRATE) { entry ->
            CalibrationScreen(onBack = { entry.ifResumed { navController.popBackStack() } })
        }
    }
}

/** Evita navegação dupla com toques repetidos durante a transição. */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
