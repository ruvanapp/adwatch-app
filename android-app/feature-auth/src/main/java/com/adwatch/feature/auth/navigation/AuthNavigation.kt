package com.adwatch.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.adwatch.feature.auth.screen.LoginScreen
import com.adwatch.feature.auth.screen.SignupScreen

fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation(
        route = "auth",
        startDestination = "auth/login"
    ) {
        composable("auth/login") {
            LoginScreen(
                onNavigateToSignup = { navController.navigate("auth/signup") },
                onLoginSuccess = { 
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        
        composable("auth/signup") {
            SignupScreen(
                onNavigateToLogin = { navController.navigate("auth/login") },
                onSignupSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
    }
}
