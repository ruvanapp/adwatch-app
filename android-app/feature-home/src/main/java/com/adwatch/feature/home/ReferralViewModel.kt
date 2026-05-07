package com.adwatch.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReferralUiState(
    val isLoading: Boolean = true,
    val referralCode: String = "",
    val referralLink: String = "",
    val currentReferralEarnings: Int = 0,
    val invitedUsersCount: Int = 0,
    val activeReferralsCount: Int = 0,
    val pendingReferralsCount: Int = 0,
    val approvedReferralsCount: Int = 0,
    val rejectedReferralsCount: Int = 0,
    val totalEarnings: Int = 0,
    val relationships: List<ReferralRelationshipItem> = emptyList(),
    val leaders: List<ReferralLeaderItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val referralApiService: ReferralApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReferralUiState())
    val uiState: StateFlow<ReferralUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val summaryDeferred = async { referralApiService.getReferralSummary() }
                val relationshipsDeferred = async { referralApiService.getReferralRelationships() }
                val leaderboardDeferred = async { referralApiService.getReferralLeaderboard() }

                val summary = summaryDeferred.await()
                val relationships = relationshipsDeferred.await()
                val leaderboard = leaderboardDeferred.await()

                if (summary.success && summary.data != null) {
                    _uiState.value = ReferralUiState(
                        isLoading = false,
                        referralCode = summary.data.referralCode,
                        referralLink = summary.data.referralLink,
                        currentReferralEarnings = summary.data.currentReferralEarnings,
                        invitedUsersCount = summary.data.invitedUsersCount,
                        activeReferralsCount = summary.data.activeReferralsCount,
                        pendingReferralsCount = summary.data.pendingReferralsCount,
                        approvedReferralsCount = summary.data.approvedReferralsCount,
                        rejectedReferralsCount = summary.data.rejectedReferralsCount,
                        totalEarnings = summary.data.totalEarnings,
                        relationships = relationships.data?.relationships.orEmpty(),
                        leaders = leaderboard.data?.leaders.orEmpty(),
                        error = relationships.error ?: leaderboard.error
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = summary.error ?: "Failed to load referrals"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load referrals"
                )
            }
        }
    }
}