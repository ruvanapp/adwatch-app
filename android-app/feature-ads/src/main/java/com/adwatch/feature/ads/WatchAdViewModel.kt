package com.adwatch.feature.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WatchAdUiState(
    val adState: AdState = AdState.Idle,
    val sessionId: String? = null,
    val creditsEarned: Int = 0,
    val totalEarnedToday: Int = 0,
    val isClaimingReward: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class WatchAdViewModel @Inject constructor(
    val rewardedAdManager: RewardedAdManager,
    private val adsApiService: AdsApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchAdUiState())
    val uiState: StateFlow<WatchAdUiState> = _uiState.asStateFlow()

    init {
        // Observe ad state changes
        viewModelScope.launch {
            rewardedAdManager.adState.collect { adState ->
                _uiState.value = _uiState.value.copy(adState = adState)
            }
        }
    }

    fun loadAd() {
        rewardedAdManager.loadAd()
    }

    fun startAdSession() {
        viewModelScope.launch {
            try {
                val response = adsApiService.startSession(StartSessionRequest())
                if (response.success && response.data?.sessionId != null) {
                    _uiState.value = _uiState.value.copy(
                        sessionId = response.data.sessionId,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = response.error ?: "Cannot watch ad right now"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to start session"
                )
            }
        }
    }

    fun onAdRewarded(amount: Int, type: String) {
        val sessionId = _uiState.value.sessionId ?: return
        claimReward(sessionId, amount, type)
    }

    private fun claimReward(sessionId: String, amount: Int, type: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClaimingReward = true)

            try {
                val response = adsApiService.claimReward(
                    ClaimRewardRequest(
                        sessionId = sessionId,
                        sdkRewardAmount = amount,
                        sdkRewardType = type,
                        idempotencyKey = UUID.randomUUID().toString()
                    )
                )

                if (response.success) {
                    val earned = response.data?.creditsEarned ?: 0
                    _uiState.value = _uiState.value.copy(
                        isClaimingReward = false,
                        creditsEarned = earned,
                        totalEarnedToday = _uiState.value.totalEarnedToday + earned,
                        message = "Earned $earned credits!",
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isClaimingReward = false,
                        error = response.error ?: "Failed to claim reward"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isClaimingReward = false,
                    error = e.message ?: "Failed to claim reward"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
        rewardedAdManager.resetState()
    }
}
