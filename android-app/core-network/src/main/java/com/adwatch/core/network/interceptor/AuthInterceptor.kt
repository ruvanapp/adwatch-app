package com.adwatch.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val newRequest = if (SessionManager.userId != null) {
            originalRequest.newBuilder()
                .header("X-Dev-User-Id", SessionManager.userId!!)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}
