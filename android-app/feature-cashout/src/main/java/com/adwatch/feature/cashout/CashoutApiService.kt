package com.adwatch.feature.cashout

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class CashoutRequestBody(
    val paypalEmail: String,
    val requestedCredits: Int
)

@Serializable
data class CashoutApiResponse(
    val success: Boolean,
    val data: CashoutResponseData? = null,
    val error: String? = null
)

@Serializable
data class CashoutResponseData(
    val message: String? = null,
    val cashoutId: String? = null
)

@Serializable
data class CashoutListResponse(
    val success: Boolean,
    val data: CashoutListData? = null,
    val error: String? = null
)

@Serializable
data class CashoutListData(
    val cashouts: List<CashoutItem> = emptyList()
)

@Serializable
data class CashoutItem(
    val id: String = "",
    val paypalEmail: String = "",
    val requestedCredits: Int = 0,
    val requestedAmountUsd: String = "0.00",
    val status: String = "pending",
    val createdAt: String = ""
)

interface CashoutApiService {
    @POST("cashouts/request")
    suspend fun requestCashout(@Body request: CashoutRequestBody): CashoutApiResponse

    @GET("cashouts")
    suspend fun getCashouts(): CashoutListResponse
}
