package com.adwatch.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val newRequestBuilder = originalRequest.newBuilder()
        val authToken = SessionManager.authToken
        val userId = SessionManager.userId

        if (!authToken.isNullOrBlank()) {
            newRequestBuilder.header("Authorization", "Bearer $authToken")
        } else if (!userId.isNullOrBlank()) {
            newRequestBuilder.header("X-Dev-User-Id", userId)
        }

        return chain.proceed(newRequestBuilder.build())
    }
}
