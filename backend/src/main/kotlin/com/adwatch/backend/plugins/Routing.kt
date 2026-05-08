package com.adwatch.backend.plugins

import com.adwatch.backend.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("AdWatch API v1.0.0")
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy", "version" to "1.0.0"))
        }

        // Admin panel (static HTML served from classpath)
        get("/admin-panel") {
            val inputStream = this::class.java.classLoader.getResourceAsStream("static/admin.html")
            if (inputStream != null) {
                val html = inputStream.bufferedReader().use { it.readText() }
                call.respondText(html, ContentType.Text.Html)
            } else {
                call.respondText("Admin panel not available", status = HttpStatusCode.NotFound)
            }
        }

        get("/privacy-policy") {
            val inputStream = this::class.java.classLoader.getResourceAsStream("static/privacy-policy.html")
            if (inputStream != null) {
                val html = inputStream.bufferedReader().use { it.readText() }
                call.respondText(html, ContentType.Text.Html)
            } else {
                call.respondText("Privacy policy not available", status = HttpStatusCode.NotFound)
            }
        }

        // Auth routes
        authRoutes()

        // User routes
        userRoutes()

        // Ads routes
        adsRoutes()

        // Wallet routes
        walletRoutes()

        // Referral routes
        referralRoutes()

        // Cashout routes
        cashoutRoutes()

        // Admin routes
        adminRoutes()

        // Analytics routes
        analyticsRoutes()
    }
}
