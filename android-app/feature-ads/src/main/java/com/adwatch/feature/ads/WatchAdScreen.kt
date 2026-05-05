package com.adwatch.feature.ads

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun WatchAdScreen(
    onBackToHome: () -> Unit,
    viewModel: WatchAdViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Load ad on first composition
    LaunchedEffect(Unit) {
        viewModel.loadAd()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Watch & Earn",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Credits earned today
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Earned Today",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "${uiState.totalEarnedToday} credits",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Ad state indicator
        when (uiState.adState) {
            is AdState.Idle -> {
                Text(
                    text = "Tap below to load an ad",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            is AdState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading ad...")
            }
            is AdState.Ready -> {
                Text(
                    text = "Ad ready! Tap to watch and earn credits.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is AdState.Showing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Watching ad...")
            }
            is AdState.Rewarded -> {
                val rewarded = uiState.adState as AdState.Rewarded
                Text(
                    text = "You earned ${rewarded.amount} reward!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is AdState.Error -> {
                val error = uiState.adState as AdState.Error
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Success message
        if (uiState.message != null) {
            Text(
                text = uiState.message!!,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Error message
        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Watch Ad Button
        Button(
            onClick = {
                if (uiState.adState is AdState.Ready && activity != null) {
                    viewModel.startAdSession()
                    viewModel.rewardedAdManager.showAd(activity) { amount, type ->
                        viewModel.onAdRewarded(amount, type)
                    }
                } else if (uiState.adState is AdState.Idle || uiState.adState is AdState.Error) {
                    viewModel.clearMessage()
                    viewModel.loadAd()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.adState is AdState.Ready || uiState.adState is AdState.Idle || uiState.adState is AdState.Error
        ) {
            when (uiState.adState) {
                is AdState.Ready -> Text("Watch Ad to Earn", style = MaterialTheme.typography.titleMedium)
                is AdState.Loading -> Text("Loading...")
                is AdState.Showing -> Text("Watching...")
                else -> Text("Load Ad", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (uiState.isClaimingReward) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Claiming reward...", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Home")
        }
    }
}
