package com.adwatch.feature.home

import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class HomeApiResponse(
    val success: Boolean,
    val data: HomeData? = null,
    val error: String? = null
)

@Serializable
data class HomeData(
    val user: UserInfo? = null,
    val wallet: WalletInfo? = null,
    val dailyProgress: DailyProgressInfo? = null,
    val canWatchAd: Boolean = false,
    val nextAdAvailableIn: Int? = null
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String? = null,
    val country: String = "",
    val status: String = "active"
)

@Serializable
data class WalletInfo(
    val userId: String = "",
    val availableCredits: Int = 0,
    val pendingCredits: Int = 0,
    val reservedCredits: Int = 0,
    val lifetimeEarnedCredits: Int = 0
)

@Serializable
data class DailyProgressInfo(
    val adsWatchedToday: Int = 0,
    val dailyLimit: Int = 0,
    val creditsEarnedToday: Int = 0
)

interface HomeApiService {
    @GET("home")
    suspend fun getHome(): HomeApiResponse
}
