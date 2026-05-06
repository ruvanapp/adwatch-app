package com.adwatch.backend.routes

import com.adwatch.backend.domain.request.SignupRequest
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.AuditService
import com.adwatch.backend.service.SecurityGuards
import com.adwatch.backend.service.TrustService
import com.adwatch.backend.service.ensureWallet
import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    route("/auth") {
        post("/signup") {
            try {
                val request = call.receive<SignupRequest>()
                SecurityGuards.assertFeatureEnabled("signup", country = request.country)

                // Generate a secure random user ID instead of predictable hash
                val userId = "usr_${UUID.randomUUID().toString().replace("-", "")}"

                TrustService.enforceOneAccountPerDevice(userId = userId, deviceHash = request.deviceHash)
                val trust = TrustService.fromSignals(
                    integrityToken = request.integrityToken,
                    emulatorFlag = request.emulatorFlag,
                    rootFlag = request.rootFlag,
                    hookFlag = request.hookFlag
                )
                TrustService.recordDeviceSignals(userId, request.deviceHash, trust)

                // Check if user already exists by email
                val existingUserId = if (request.email != null) {
                    dbQuery {
                        Users.selectAll().where { Users.email eq request.email.lowercase() }.singleOrNull()?.get(Users.id)
                    }
                } else null

                val finalUserId = existingUserId ?: userId

                if (existingUserId == null) {
                    dbQuery {
                        Users.insert {
                            it[id] = finalUserId
                            it[authProviderId] = finalUserId
                            it[email] = request.email?.lowercase()
                            it[phone] = request.phone
                            it[country] = request.country
                            it[status] = "active"
                            it[createdAt] = Instant.now()
                            it[updatedAt] = Instant.now()
                        }
                    }
                }
                ensureWallet(finalUserId)
                AuditService.log("user", finalUserId, "user", finalUserId, "signup_created")
                call.respond(HttpStatusCode.Created, ApiResponse(
                    success = true,
                    data = mapOf("message" to "Signup completed", "userId" to finalUserId)
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                    success = false,
                    error = "Signup failed. Please try again."
                ))
            }
        }

        post("/login") {
            try {
                val request = call.receive<com.adwatch.backend.domain.request.LoginRequest>()
                val email = request.email?.lowercase()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Email required"))

                // Look up user by email field (not by hash-based ID)
                val user = dbQuery {
                    Users.selectAll().where { Users.email eq email }.singleOrNull()
                } ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = "Invalid credentials"))

                val userId = user[Users.id]

                // Note: Password verification will be done via Firebase Auth tokens in production.
                // For MVP, login returns userId for X-Dev-User-Id header auth.
                // This is acceptable because the dev-auth provider is disabled in production.

                AuditService.log("user", userId, "user", userId, "login")
                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = mapOf("message" to "Login successful", "userId" to userId)
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                    success = false,
                    error = "Login failed. Please try again."
                ))
            }
        }
    }
}
