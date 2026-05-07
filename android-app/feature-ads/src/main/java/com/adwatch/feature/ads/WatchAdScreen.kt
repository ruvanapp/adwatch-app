package com.adwatch.feature.ads

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adwatch.core.ui.R

@Composable
fun WatchAdScreen(
    onBackToHome: () -> Unit,
    viewModel: WatchAdViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

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
            text = stringResource(R.string.ad_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.ad_earned_today), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(R.string.credits_label, uiState.totalEarnedToday),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (uiState.adState) {
            is AdState.Idle -> {
                Text(
                    text = stringResource(R.string.ad_tap_load),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            is AdState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.ad_loading))
            }
            is AdState.Ready -> {
                Text(
                    text = stringResource(R.string.ad_ready),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is AdState.Showing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.ad_watching))
            }
            is AdState.Rewarded -> {
                val rewarded = uiState.adState as AdState.Rewarded
                Text(
                    text = stringResource(R.string.ad_rewarded, rewarded.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is AdState.Error -> {
                Text(
                    text = (uiState.adState as AdState.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.message != null) {
            Text(text = uiState.message!!, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.error != null) {
            Text(text = uiState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

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
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = uiState.adState is AdState.Ready || uiState.adState is AdState.Idle || uiState.adState is AdState.Error
        ) {
            Text(
                when (uiState.adState) {
                    is AdState.Ready -> stringResource(R.string.ad_watch_btn)
                    is AdState.Loading -> stringResource(R.string.ad_loading)
                    is AdState.Showing -> stringResource(R.string.ad_watching)
                    else -> stringResource(R.string.ad_load_btn)
                },
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (uiState.isClaimingReward) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.ad_claiming), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onBackToHome, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ad_back))
        }
    }
}
