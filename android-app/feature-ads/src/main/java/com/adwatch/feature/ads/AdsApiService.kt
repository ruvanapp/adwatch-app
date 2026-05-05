package com.adwatch.feature.ads

import com.adwatch.core.network.model.ApiResult
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class StartSessionRequest(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault val adNetwork: String = "admob",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault val adUnitId: String = RewardedAdManager.AD_UNIT_ID,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault val placement: String = "main_earn",
    val deviceId: String? = null,
    val deviceHash: String? = null,
    val integrityToken: String? = null,
    val emulatorFlag: Boolean? = null,
    val rootFlag: Boolean? = null,
    val hookFlag: Boolean? = null
)

@Serializable
data class ClaimRewardRequest(
    val sessionId: String,
    val sdkRewardAmount: Int? = null,
    val sdkRewardType: String? = null,
    val deviceId: String? = null,
    val idempotencyKey: String? = null
)

@Serializable
data class StartSessionResponse(
    val success: Boolean,
    val data: SessionData? = null,
    val error: String? = null
)

@Serializable
data class SessionData(
    val sessionId: String? = null,
    val message: String? = null,
    val riskScore: Int? = null
)

@Serializable
data class ClaimRewardResponse(
    val success: Boolean,
    val data: ClaimData? = null,
    val error: String? = null
)

@Serializable
data class ClaimData(
    val message: String? = null,
    val sessionId: String? = null,
    val creditsEarned: Int? = null
)

interface AdsApiService {
    @POST("ads/session/start")
    suspend fun startSession(@Body request: StartSessionRequest): StartSessionResponse

    @POST("ads/session/claim-reward")
    suspend fun claimReward(@Body request: ClaimRewardRequest): ClaimRewardResponse
}
