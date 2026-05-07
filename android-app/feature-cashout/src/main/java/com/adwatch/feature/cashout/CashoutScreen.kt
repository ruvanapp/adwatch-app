package com.adwatch.feature.cashout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adwatch.core.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashoutScreen(
    onBack: () -> Unit,
    viewModel: CashoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cashout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("<") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.cashout_paypal_info), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.cashout_paypal_desc), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.paypalEmail,
                    onValueChange = viewModel::onPaypalEmailChanged,
                    label = { Text(stringResource(R.string.cashout_paypal_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !uiState.isSubmitting
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.creditsToWithdraw,
                    onValueChange = viewModel::onCreditsChanged,
                    label = { Text(stringResource(R.string.cashout_credits_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        val credits = uiState.creditsToWithdraw.toIntOrNull() ?: 0
                        if (credits > 0) {
                            Text(stringResource(R.string.cashout_usd_equiv, viewModel.getUsdEquivalent()))
                        }
                    },
                    enabled = !uiState.isSubmitting
                )
            }

            if (uiState.error != null) {
                item {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (uiState.message != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(uiState.message!!, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::submitCashout,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !uiState.isSubmitting && uiState.paypalEmail.isNotBlank() && uiState.creditsToWithdraw.isNotBlank()
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.cashout_btn))
                    }
                }
            }

            if (uiState.cashoutHistory.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.cashout_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                }
                items(uiState.cashoutHistory) { item ->
                    CashoutHistoryCard(item)
                }
            }
        }
    }
}

@Composable
private fun CashoutHistoryCard(item: CashoutItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(item.paypalEmail, style = MaterialTheme.typography.bodyMedium)
                Text("${item.requestedCredits} credits (\$${item.requestedAmountUsd})", style = MaterialTheme.typography.bodySmall)
                Text(item.createdAt.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(item.status)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (color, text) = when (status.lowercase()) {
        "pending" -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.cashout_status_pending)
        "approved" -> MaterialTheme.colorScheme.primary to stringResource(R.string.cashout_status_approved)
        "paid" -> MaterialTheme.colorScheme.primary to stringResource(R.string.cashout_status_paid)
        "rejected" -> MaterialTheme.colorScheme.error to stringResource(R.string.cashout_status_rejected)
        "hold" -> MaterialTheme.colorScheme.secondary to stringResource(R.string.cashout_status_hold)
        else -> MaterialTheme.colorScheme.outline to status
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}
