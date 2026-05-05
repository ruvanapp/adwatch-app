package com.adwatch.backend.domain.request

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String? = null,
    val phone: String? = null,
    val country: String,
    val deviceHash: String? = null,
    val integrityToken: String? = null,
    val emulatorFlag: Boolean? = null,
    val rootFlag: Boolean? = null,
    val hookFlag: Boolean? = null
)

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null
)

@Serializable
data class StartAdSessionRequest(
    val adNetwork: String,
    val adUnitId: String,
    val placement: String,
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
data class CashoutRequestDto(
    val paypalEmail: String,
    val requestedCredits: Int
)

@Serializable
data class ReviewCashoutRequest(
    val approved: Boolean,
    val hold: Boolean = false,
    val notes: String?
)

@Serializable
data class UpsertRewardRuleRequest(
    val countryGroup: String,
    val creditsPerCompletion: Int,
    val dailyCap: Int = 300,
    val hourlyCap: Int = 40,
    val cooldownSeconds: Int = 15,
    val active: Boolean = true
)

@Serializable
data class UpdateFeatureFlagRequest(
    val enabled: Boolean,
    val countryScope: String? = null,
    val updatedBy: String? = "admin"
)
