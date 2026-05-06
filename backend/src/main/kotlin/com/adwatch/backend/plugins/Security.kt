package com.adwatch.backend.plugins

import com.google.firebase.auth.FirebaseAuth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*

class DevUserPrincipal(val userId: String) : Principal
class AdminPrincipal(val adminId: String) : Principal

class DevAuthProvider(config: Config) : AuthenticationProvider(config) {
    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val devUserId = call.request.headers["X-Dev-User-Id"]
        if (devUserId != null) {
            context.principal(DevUserPrincipal(devUserId))
        }
    }
}

class AdminAuthProvider(config: Config) : AuthenticationProvider(config) {
    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val apiKey = call.request.headers["X-Admin-Api-Key"]
        val expectedKey = System.getenv("ADMIN_API_KEY")
        if (!expectedKey.isNullOrBlank() && apiKey == expectedKey) {
            context.principal(AdminPrincipal("admin"))
        }
    }
}

fun Application.configureSecurity() {
    val appEnv = environment.config.propertyOrNull("app.env")?.getString() ?: "production"
    val isDev = appEnv == "development"
    val jwtSecret = environment.config.propertyOrNull("app.jwtSecret")?.getString() ?: "fallback-secret"

    install(Authentication) {
        // Dev mode auth: checks X-Dev-User-Id header
        register(DevAuthProvider(DevAuthProvider.Config("dev-auth")))

        // Admin auth: requires X-Admin-Api-Key header matching ADMIN_API_KEY env var
        register(AdminAuthProvider(AdminAuthProvider.Config("admin-auth")))

        // Firebase auth: verifies Firebase ID tokens
        jwt("firebase-auth") {
            verifier(
                com.auth0.jwt.JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256(jwtSecret)).build()
            )

            validate { credential ->
                try {
                    val token = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
                    if (!token.isNullOrBlank()) {
                        val decodedToken = FirebaseAuth.getInstance().verifyIdToken(token)
                        val uid = decodedToken.uid
                        if (uid != null) {
                            return@validate JWTPrincipal(credential.payload)
                        }
                    }
                } catch (e: Exception) {
                    // Firebase verification failed
                }
                null
            }

            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }
    }
}
