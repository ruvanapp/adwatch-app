package com.adwatch.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

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
            anyHost() // Allow mobile app requests from any origin
        }
    }
}
