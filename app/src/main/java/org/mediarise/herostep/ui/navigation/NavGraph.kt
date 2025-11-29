package org.mediarise.herostep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mediarise.herostep.ui.screens.*

@Composable
fun GameNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        composable(Screen.IntroVideo.route) {
            IntroVideoScreen(navController = navController)
        }
        composable(Screen.MainMenu.route) {
            MainMenuScreen(navController = navController)
        }
        composable(Screen.RaceSelection.route) {
            RaceSelectionScreen(navController = navController)
        }
        composable(
            route = "${Screen.Game.route}/{race}/{profession}/{heroName}",
            arguments = listOf(
                navArgument("race") {
                    type = NavType.StringType
                },
                navArgument("profession") {
                    type = NavType.StringType
                },
                navArgument("heroName") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val raceName = backStackEntry.arguments?.getString("race") ?: "HUMANS"
            val professionName = backStackEntry.arguments?.getString("profession") ?: "WARRIOR"
            val heroName = backStackEntry.arguments?.getString("heroName") ?: "Hero"
            val race = org.mediarise.herostep.data.model.Race.valueOf(raceName)
            val profession = org.mediarise.herostep.data.model.Profession.valueOf(professionName)
            GameScreen(race = race, profession = profession, heroName = heroName)
        }
    }
}

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object IntroVideo : Screen("intro_video")
    object MainMenu : Screen("main_menu")
    object RaceSelection : Screen("race_selection")
    object Game : Screen("game")
}

