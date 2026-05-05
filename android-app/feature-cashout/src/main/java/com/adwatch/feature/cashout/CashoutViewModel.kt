package com.adwatch.feature.cashout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CashoutUiState(
    val paypalEmail: String = "",
    val creditsToWithdraw: String = "",
    val availableCredits: Int = 0,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val cashoutHistory: List<CashoutItem> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class CashoutViewModel @Inject constructor(
    private val cashoutApiService: CashoutApiService
) : ViewModel() {

    companion object {
        const val MIN_CASHOUT_CREDITS = 500
        const val CREDITS_PER_DOLLAR = 100
    }

    private val _uiState = MutableStateFlow(CashoutUiState())
    val uiState: StateFlow<CashoutUiState> = _uiState.asStateFlow()

    init {
        loadCashoutHistory()
    }

    fun onPaypalEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(paypalEmail = email, error = null)
    }

    fun onCreditsChanged(credits: String) {
        _uiState.value = _uiState.value.copy(creditsToWithdraw = credits, error = null)
    }

    fun getUsdEquivalent(): String {
        val credits = _uiState.value.creditsToWithdraw.toIntOrNull() ?: 0
        return String.format("%.2f", credits / CREDITS_PER_DOLLAR.toDouble())
    }

    fun submitCashout() {
        val credits = _uiState.value.creditsToWithdraw.toIntOrNull()
        if (credits == null || credits < MIN_CASHOUT_CREDITS) {
            _uiState.value = _uiState.value.copy(error = "Minimum cashout is $MIN_CASHOUT_CREDITS credits (\$${MIN_CASHOUT_CREDITS / CREDITS_PER_DOLLAR})")
            return
        }
        if (!_uiState.value.paypalEmail.contains("@")) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid PayPal email")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)

            try {
                val response = cashoutApiService.requestCashout(
                    CashoutRequestBody(
                        paypalEmail = _uiState.value.paypalEmail,
                        requestedCredits = credits
                    )
                )

                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        submitted = true,
                        message = "Cashout request submitted! It will be reviewed within 24-48 hours.",
                        paypalEmail = "",
                        creditsToWithdraw = ""
                    )
                    loadCashoutHistory()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = response.error ?: "Failed to submit cashout"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }

    private fun loadCashoutHistory() {
        viewModelScope.launch {
            try {
                val response = cashoutApiService.getCashouts()
                if (response.success && response.data != null) {
                    _uiState.value = _uiState.value.copy(
                        cashoutHistory = response.data.cashouts
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, submitted = false)
    }
}
