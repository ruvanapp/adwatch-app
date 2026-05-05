package com.adwatch.feature.wallet

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class WalletApiResponse(
    val success: Boolean,
    val data: WalletData? = null,
    val error: String? = null
)

@Serializable
data class WalletData(
    val wallet: WalletInfo? = null,
    val creditsToUsdRate: Double = 100.0
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
data class LedgerApiResponse(
    val success: Boolean,
    val data: LedgerData? = null,
    val error: String? = null
)

@Serializable
data class LedgerData(
    val entries: List<LedgerEntryInfo> = emptyList(),
    val pagination: PaginationInfo? = null
)

@Serializable
data class LedgerEntryInfo(
    val id: String = "",
    val type: String = "",
    val creditsDelta: Int = 0,
    val sourceType: String = "",
    val status: String = "",
    val createdAt: String = ""
)

@Serializable
data class PaginationInfo(
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalItems: Int = 0,
    val totalPages: Int = 0
)

interface WalletApiService {
    @GET("wallet")
    suspend fun getWallet(): WalletApiResponse

    @GET("wallet/ledger")
    suspend fun getLedger(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): LedgerApiResponse
}
