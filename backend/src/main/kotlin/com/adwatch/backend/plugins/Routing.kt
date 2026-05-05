package com.adwatch.backend.plugins

import com.adwatch.backend.routes.*
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
