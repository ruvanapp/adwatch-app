package com.adwatch.feature.auth

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class SignupRequest(
    val email: String,
    val password: String,
    val country: String,
    val deviceHash: String = "android_device",
    val integrityToken: String? = null,
    val emulatorFlag: Boolean = false,
    val rootFlag: Boolean = false,
    val hookFlag: Boolean = false
)

@Serializable
data class SignupResponse(
    val success: Boolean,
    val data: SignupData? = null,
    val error: String? = null
)

@Serializable
data class SignupData(
    val message: String,
    val userId: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class GoogleLoginRequest(
    val country: String,
    val deviceHash: String = "android_device"
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val data: LoginData? = null,
    val error: String? = null
)

@Serializable
data class LoginData(
    val message: String,
    val token: String? = null,
    val userId: String? = null
)

interface AuthApiService {
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): SignupResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/google")
    suspend fun loginWithGoogle(
        @Header("Authorization") authorization: String,
        @Body request: GoogleLoginRequest
    ): LoginResponse
}
