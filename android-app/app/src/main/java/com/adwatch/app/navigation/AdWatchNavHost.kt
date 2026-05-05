package com.adwatch.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.adwatch.feature.ads.WatchAdScreen
import com.adwatch.feature.auth.navigation.authGraph
import com.adwatch.feature.home.navigation.homeGraph
import com.adwatch.feature.wallet.navigation.walletGraph
import com.adwatch.feature.cashout.navigation.cashoutGraph

@Composable
fun AdWatchNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "auth",
        modifier = modifier
    ) {
        authGraph(navController)
        homeGraph(navController)
        walletGraph(navController)
        cashoutGraph(navController)

        // Watch ad screen (reached from home)
        composable("home/watch-ad") {
            WatchAdScreen(
                onBackToHome = { navController.popBackStack() }
            )
        }
    }
}
