package com.adwatch.feature.wallet.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.adwatch.feature.wallet.WalletScreen

fun NavGraphBuilder.walletGraph(navController: NavHostController) {
    navigation(route = "wallet", startDestination = "wallet/main") {
        composable("wallet/main") {
            WalletScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
