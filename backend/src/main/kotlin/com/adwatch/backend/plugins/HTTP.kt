package com.adwatch.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureHTTP() {
    val appEnv = environment.config.propertyOrNull("app.env")?.getString() ?: "production"

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Dev-User-Id")
        allowHeader("X-Admin-Api-Key")

        if (appEnv == "development") {
            anyHost()
        } else {
            allowHost("adwatch-app-production.up.railway.app", schemes = listOf("https"))
        }
    }

    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }
        register(RateLimitName("api")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
        register(RateLimitName("cashout")) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
        }
    }
}
