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
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("static/admin.html")
                ?: this@configureRouting.javaClass.classLoader.getResourceAsStream("static/admin.html")
            if (inputStream != null) {
                val html = inputStream.bufferedReader().use { it.readText() }
                call.respondText(html, ContentType.Text.Html)
            } else {
                call.respondText("Admin panel loading failed", status = HttpStatusCode.InternalServerError)
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

        // Cashout routes
        cashoutRoutes()

        // Admin routes
        adminRoutes()

        // Analytics routes
        analyticsRoutes()
    }
}
