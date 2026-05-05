package com.adwatch.feature.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletUiState(
    val isLoading: Boolean = true,
    val availableCredits: Int = 0,
    val pendingCredits: Int = 0,
    val reservedCredits: Int = 0,
    val lifetimeCredits: Int = 0,
    val usdEquivalent: String = "$0.00",
    val ledgerEntries: List<LedgerEntryInfo> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletApiService: WalletApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var currentPage = 1

    init {
        loadWallet()
        loadLedger()
    }

    fun loadWallet() {
        viewModelScope.launch {
            try {
                val response = walletApiService.getWallet()
                if (response.success && response.data != null) {
                    val wallet = response.data.wallet
                    val credits = wallet?.availableCredits ?: 0
                    val usd = credits / (response.data.creditsToUsdRate)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        availableCredits = credits,
                        pendingCredits = wallet?.pendingCredits ?: 0,
                        reservedCredits = wallet?.reservedCredits ?: 0,
                        lifetimeCredits = wallet?.lifetimeEarnedCredits ?: 0,
                        usdEquivalent = "$${String.format("%.2f", usd)}",
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun loadLedger() {
        viewModelScope.launch {
            try {
                val response = walletApiService.getLedger(page = currentPage)
                if (response.success && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        ledgerEntries = response.data.entries,
                        hasMore = (response.data.pagination?.page ?: 1) < (response.data.pagination?.totalPages ?: 1),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun loadMore() {
        currentPage++
        viewModelScope.launch {
            try {
                val response = walletApiService.getLedger(page = currentPage)
                if (response.success && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        ledgerEntries = _uiState.value.ledgerEntries + response.data.entries,
                        hasMore = (response.data.pagination?.page ?: 1) < (response.data.pagination?.totalPages ?: 1)
                    )
                }
            } catch (_: Exception) {}
        }
    }
}
