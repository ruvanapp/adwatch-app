package com.adwatch.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.adwatch.feature.home.HomeScreen

fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    navigation(route = "home", startDestination = "home/main") {
        composable("home/main") {
            HomeScreen(
                onNavigateToWatchAd = { navController.navigate("home/watch-ad") },
                onNavigateToWallet = { navController.navigate("wallet/main") },
                onNavigateToCashout = { navController.navigate("cashout/request") }
            )
        }
    }
}
