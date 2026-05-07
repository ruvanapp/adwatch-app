package com.adwatch.backend.plugins

import com.google.firebase.auth.FirebaseAuth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*

class DevUserPrincipal(val userId: String) : Principal
class AdminPrincipal(val adminId: String) : Principal
class FirebaseUserPrincipal(val userId: String, val email: String?) : Principal

class DevAuthProvider(config: Config) : AuthenticationProvider(config) {
    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val appEnv = call.application.environment.config.propertyOrNull("app.env")?.getString() ?: "production"
        if (appEnv != "development") return
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

class FirebaseAuthProvider(config: Config) : AuthenticationProvider(config) {
    class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.request.headers["Authorization"]
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()
        if (token.isBlank()) return

        try {
            val decodedToken = FirebaseAuth.getInstance().verifyIdToken(token)
            context.principal(
                FirebaseUserPrincipal(
                    userId = decodedToken.uid,
                    email = decodedToken.email
                )
            )
        } catch (_: Exception) {
        }
    }
}

fun Application.configureSecurity() {
    install(Authentication) {
        register(DevAuthProvider(DevAuthProvider.Config("dev-auth")))
        register(AdminAuthProvider(AdminAuthProvider.Config("admin-auth")))
        register(FirebaseAuthProvider(FirebaseAuthProvider.Config("firebase-auth")))
    }
}
