package com.adwatch.feature.cashout.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.adwatch.feature.cashout.CashoutScreen

fun NavGraphBuilder.cashoutGraph(navController: NavHostController) {
    navigation(route = "cashout", startDestination = "cashout/request") {
        composable("cashout/request") {
            CashoutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
