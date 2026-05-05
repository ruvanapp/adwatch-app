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
                val userId = request.email?.lowercase()?.let { "usr_${it.hashCode().toString().replace("-", "m")}" }
                    ?: request.phone?.let { "usr_${it.hashCode().toString().replace("-", "m")}" }
                    ?: "usr_${java.util.UUID.randomUUID()}"

                TrustService.enforceOneAccountPerDevice(userId = userId, deviceHash = request.deviceHash)
                val trust = TrustService.fromSignals(
                    integrityToken = request.integrityToken,
                    emulatorFlag = request.emulatorFlag,
                    rootFlag = request.rootFlag,
                    hookFlag = request.hookFlag
                )
                TrustService.recordDeviceSignals(userId, request.deviceHash, trust)

                dbQuery {
                    val existing = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                    if (existing == null) {
                        Users.insert {
                            it[id] = userId
                            it[authProviderId] = userId
                            it[email] = request.email
                            it[phone] = request.phone
                            it[country] = request.country
                            it[status] = "active"
                            it[createdAt] = Instant.now()
                            it[updatedAt] = Instant.now()
                        }
                    }
                }
                ensureWallet(userId)
                AuditService.log("user", userId, "user", userId, "signup_created")
                call.respond(HttpStatusCode.Created, ApiResponse(
                    success = true,
                    data = mapOf("message" to "Signup completed", "userId" to userId)
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                    success = false,
                    error = e.message ?: "Invalid request"
                ))
            }
        }
        
        post("/login") {
            try {
                val request = call.receive<com.adwatch.backend.domain.request.LoginRequest>()
                val email = request.email?.lowercase()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Email required"))

                val userId = email.let { "usr_${it.hashCode().toString().replace("-", "m")}" }
                val user = dbQuery {
                    Users.selectAll().where { Users.id eq userId }.singleOrNull()
                } ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = "Invalid credentials"))

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = mapOf("message" to "Login successful", "userId" to userId)
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                    success = false,
                    error = e.message ?: "Invalid request"
                ))
            }
        }
    }
}
