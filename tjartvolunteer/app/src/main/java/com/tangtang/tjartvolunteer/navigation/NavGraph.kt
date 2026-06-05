package com.tangtang.tjartvolunteer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tangtang.tjartvolunteer.ui.screen.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object Routes {
    const val HOME = "home"
    const val RESULT = "result"
    const val UNIVERSITY_LIST = "university_list"
    const val UNIVERSITY_DETAIL = "university_detail/{name}"
    const val PROFILE = "profile"
    const val SYNC_LOG = "sync_log"

    fun universityDetail(name: String) = "university_detail/$name"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(Routes.RESULT) {
            ResultScreen(navController = navController)
        }
        composable(Routes.UNIVERSITY_LIST) {
            UniversityListScreen(navController = navController)
        }
        composable(
            route = Routes.UNIVERSITY_DETAIL,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = URLDecoder.decode(
                backStackEntry.arguments?.getString("name") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            UniversityDetailScreen(name = name, navController = navController)
        }
        composable(Routes.PROFILE) {
            ProfileScreen()
        }
        composable(Routes.SYNC_LOG) {
            SyncLogScreen(navController = navController)
        }
    }
}
