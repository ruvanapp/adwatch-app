package com.adwatch.feature.home

import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class ReferralSummaryResponse(
    val success: Boolean,
    val data: ReferralSummaryData? = null,
    val error: String? = null
)

@Serializable
data class ReferralSummaryData(
    val referralCode: String,
    val referralLink: String,
    val currentReferralEarnings: Int,
    val invitedUsersCount: Int,
    val activeReferralsCount: Int,
    val pendingReferralsCount: Int,
    val approvedReferralsCount: Int,
    val rejectedReferralsCount: Int,
    val totalEarnings: Int
)

@Serializable
data class ReferralRelationshipsResponse(
    val success: Boolean,
    val data: ReferralRelationshipsData? = null,
    val error: String? = null
)

@Serializable
data class ReferralRelationshipsData(
    val relationships: List<ReferralRelationshipItem> = emptyList()
)

@Serializable
data class ReferralRelationshipItem(
    val relationshipId: String,
    val invitedUserId: String,
    val invitedEmail: String? = null,
    val status: String,
    val source: String,
    val fraudReasons: String? = null,
    val invitedAt: String,
    val approvedAt: String? = null,
    val rejectedAt: String? = null,
    val totalCommissionEarned: Int
)

@Serializable
data class ReferralLeaderboardResponse(
    val success: Boolean,
    val data: ReferralLeaderboardData? = null,
    val error: String? = null
)

@Serializable
data class ReferralLeaderboardData(
    val leaders: List<ReferralLeaderItem> = emptyList()
)

@Serializable
data class ReferralLeaderItem(
    val userId: String,
    val email: String? = null,
    val approvedReferrals: Int,
    val totalCommissionCredits: Int
)

interface ReferralApiService {
    @GET("referrals/me")
    suspend fun getReferralSummary(): ReferralSummaryResponse

    @GET("referrals/relationships")
    suspend fun getReferralRelationships(): ReferralRelationshipsResponse

    @GET("referrals/leaderboard")
    suspend fun getReferralLeaderboard(): ReferralLeaderboardResponse
}