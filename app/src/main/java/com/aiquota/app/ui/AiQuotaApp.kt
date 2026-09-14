package com.aiquota.app.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiquota.app.ui.addaccount.AddAccountScreen
import com.aiquota.app.ui.dashboard.DashboardScreen
import com.aiquota.app.ui.detail.DetailScreen
import com.aiquota.app.ui.onboarding.OnboardingScreen
import com.aiquota.app.ui.onboarding.OnboardingViewModel
import com.aiquota.app.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val DETAIL = "detail/{accountId}"
    const val SETTINGS = "settings"
    const val ADD_ACCOUNT = "addAccount"
    const val ONBOARDING = "onboarding"

    fun detail(accountId: String) = "detail/$accountId"
}

@Composable
fun AiQuotaApp() {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState(initial = null)

    // 引导状态尚未从 DataStore 读出前显示加载态，避免冷启动闪一下引导页
    if (onboardingCompleted == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (onboardingCompleted == true) Routes.DASHBOARD else Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onStart = { navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                } }
            )
        }
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