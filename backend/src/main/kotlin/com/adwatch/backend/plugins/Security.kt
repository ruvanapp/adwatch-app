package com.adwatch.backend.plugins

import com.google.firebase.auth.FirebaseAuth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

class DevUserPrincipal(val userId: String) : Principal

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

fun Application.configureSecurity() {
    install(Authentication) {
        // Dev mode auth: checks X-Dev-User-Id header
        register(DevAuthProvider(DevAuthProvider.Config("dev-auth")))

        jwt("firebase-auth") {
            verifier { 
                // Firebase Admin SDK verifies tokens; this is a no-op verifier
                com.auth0.jwt.JWT.require(com.auth0.jwt.algorithms.Algorithm.none()).build()
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
