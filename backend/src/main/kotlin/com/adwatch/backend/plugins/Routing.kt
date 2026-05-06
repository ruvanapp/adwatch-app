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

        // Admin panel (static HTML)
        get("/admin-panel") {
            val html = Thread.currentThread().contextClassLoader.getResource("static/admin.html")?.readText()
                ?: javaClass.getResource("/static/admin.html")?.readText()
            if (html != null) {
                call.respondText(html, ContentType.Text.Html)
            } else {
                call.respondText("Admin panel not found. Checked classpath for static/admin.html", status = HttpStatusCode.NotFound)
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
