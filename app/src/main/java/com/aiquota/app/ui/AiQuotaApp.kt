package com.aiquota.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiquota.app.ui.addaccount.AddAccountScreen
import com.aiquota.app.ui.dashboard.DashboardScreen
import com.aiquota.app.ui.detail.DetailScreen
import com.aiquota.app.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val DETAIL = "detail/{accountId}"
    const val SETTINGS = "settings"
    const val ADD_ACCOUNT = "addAccount"

    fun detail(accountId: String) = "detail/$accountId"
}

@Composable
fun AiQuotaApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToDetail = { id -> navController.navigate(Routes.detail(id)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) }
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADD_ACCOUNT) {
            AddAccountScreen(onBack = { navController.popBackStack() })
        }
    }
}