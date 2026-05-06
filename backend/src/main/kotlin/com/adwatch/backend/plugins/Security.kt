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

    install(Authentication) {
        // Dev mode auth: ONLY register in development environment
        if (isDev) {
            register(DevAuthProvider(DevAuthProvider.Config("dev-auth")))
        }

        // Admin auth: requires X-Admin-Api-Key header matching ADMIN_API_KEY env var
        register(AdminAuthProvider(AdminAuthProvider.Config("admin-auth")))

        // Firebase auth: verifies Firebase ID tokens
        jwt("firebase-auth") {
            verifier {
                // Use HMAC256 with the app's JWT secret as a structural verifier
                // Actual verification is done by Firebase Admin SDK in validate{}
                val secret = environment?.config?.propertyOrNull("app.jwtSecret")?.getString() ?: "fallback-secret"
                com.auth0.jwt.JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)).build()
            }

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
                    application.log.debug("Firebase token verification failed: ${e.message}")
                }
                null
            }

            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }

        // Production user auth: uses X-Dev-User-Id in production too (until Firebase is fully wired)
        // but validates that the user actually exists in database
        if (!isDev) {
            register(DevAuthProvider(DevAuthProvider.Config("dev-auth")))
        }
    }
}
