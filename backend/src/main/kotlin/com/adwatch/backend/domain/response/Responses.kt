package com.adwatch.backend.domain.response

import com.adwatch.backend.domain.model.*
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AuthResponse(
    val user: User,
    val wallet: Wallet
)

@Serializable
data class WalletResponse(
    val wallet: Wallet,
    val creditsToUsdRate: Double // e.g., 100 credits = $1.00
)

@Serializable
data class LedgerHistoryResponse(
    val entries: List<LedgerEntry>,
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int
)

@Serializable
data class AdSessionResponse(
    val sessionId: String,
    val adNetwork: String,
    val placement: String,
    val canWatch: Boolean,
    val reason: String?
)

@Serializable
data class RewardClaimResponse(
    val success: Boolean,
    val creditsEarned: Int,
    val newBalance: Int,
    val message: String?
)

@Serializable
data class CashoutResponse(
    val request: CashoutRequest
)

@Serializable
data class HomeResponse(
    val user: User,
    val wallet: Wallet,
    val dailyProgress: DailyProgress,
    val canWatchAd: Boolean,
    val nextAdAvailableIn: Int? // seconds
)

@Serializable
data class DailyProgress(
    val adsWatchedToday: Int,
    val dailyLimit: Int,
    val creditsEarnedToday: Int
)
