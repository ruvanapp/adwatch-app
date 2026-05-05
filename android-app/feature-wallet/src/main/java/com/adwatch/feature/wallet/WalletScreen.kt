package com.adwatch.feature.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Balance card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Available Balance", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${uiState.availableCredits} credits",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(uiState.usdEquivalent, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Breakdown
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Breakdown", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            BalanceRow("Available", uiState.availableCredits)
                            BalanceRow("Pending", uiState.pendingCredits)
                            BalanceRow("Reserved (cashout)", uiState.reservedCredits)
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            BalanceRow("Lifetime earned", uiState.lifetimeCredits)
                        }
                    }
                }

                // Ledger header
                item {
                    Text(
                        "Transaction History",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Ledger entries
                if (uiState.ledgerEntries.isEmpty()) {
                    item {
                        Text(
                            "No transactions yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(uiState.ledgerEntries) { entry ->
                        LedgerEntryCard(entry)
                    }
                }

                // Load more
                if (uiState.hasMore) {
                    item {
                        TextButton(
                            onClick = { viewModel.loadMore() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load More")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(label: String, credits: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$credits", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LedgerEntryCard(entry: LedgerEntryInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatEntryType(entry.type),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = entry.createdAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (entry.creditsDelta >= 0) "+${entry.creditsDelta}" else "${entry.creditsDelta}",
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.creditsDelta >= 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatEntryType(type: String): String {
    return when (type.uppercase()) {
        "AD_REWARD", "EARN" -> "Ad Reward"
        "CASHOUT_RESERVED" -> "Cashout Reserved"
        "CASHOUT_PAID" -> "Cashout Paid"
        "CASHOUT_REVERSED" -> "Cashout Returned"
        "MANUAL_ADJUSTMENT" -> "Adjustment"
        else -> type.replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}
