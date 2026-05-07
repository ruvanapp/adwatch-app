package com.adwatch.core.network.interceptor

object SessionManager {
    @Volatile
    var userId: String? = null

    @Volatile
    var authToken: String? = null
}
