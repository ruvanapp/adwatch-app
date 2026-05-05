package com.adwatch.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val availableCredits: Int = 0,
    val lifetimeCredits: Int = 0,
    val adsWatchedToday: Int = 0,
    val dailyLimit: Int = 30,
    val creditsEarnedToday: Int = 0,
    val canWatchAd: Boolean = false,
    val nextAdAvailableIn: Int? = null,
    val usdEquivalent: String = "$0.00",
    val userName: String = "",
    val country: String = "",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeApiService: HomeApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = homeApiService.getHome()
                if (response.success && response.data != null) {
                    val wallet = response.data.wallet
                    val progress = response.data.dailyProgress
                    val credits = wallet?.availableCredits ?: 0
                    val usd = credits / 100.0

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        availableCredits = credits,
                        lifetimeCredits = wallet?.lifetimeEarnedCredits ?: 0,
                        adsWatchedToday = progress?.adsWatchedToday ?: 0,
                        dailyLimit = progress?.dailyLimit ?: 30,
                        creditsEarnedToday = progress?.creditsEarnedToday ?: 0,
                        canWatchAd = response.data.canWatchAd,
                        nextAdAvailableIn = response.data.nextAdAvailableIn,
                        usdEquivalent = "$${String.format("%.2f", usd)}",
                        userName = response.data.user?.email ?: "",
                        country = response.data.user?.country ?: ""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Failed to load data"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }
}
