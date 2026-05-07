package com.adwatch.backend.routes

import com.adwatch.backend.domain.request.SignupRequest
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.AuditService
import com.adwatch.backend.service.ReferralService
import com.adwatch.backend.service.SecurityGuards
import com.adwatch.backend.service.TrustService
import com.adwatch.backend.service.ensureWallet
import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
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
                ReferralService.ensureReferralProfile(finalUserId)
                val signupIp = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
                ReferralService.attachReferral(
                    invitedUserId = finalUserId,
                    referralCode = request.referralCode,
                    deviceHash = request.deviceHash,
                    ipHash = com.adwatch.backend.service.Hashing.sha256(signupIp),
                    emulatorFlag = request.emulatorFlag == true
                )
                ensureWallet(finalUserId)
                AuditService.log("user", finalUserId, "user", finalUserId, "signup_created")
                call.respond(HttpStatusCode.Created, ApiResponse(
                    success = true,
                    data = mapOf(
                        "message" to "Signup completed",
                        "userId" to finalUserId,
                        "referralCode" to dbQuery {
                            com.adwatch.backend.data.table.ReferralProfiles.selectAll()
                                .where { com.adwatch.backend.data.table.ReferralProfiles.userId eq finalUserId }
                                .single()[com.adwatch.backend.data.table.ReferralProfiles.referralCode]
                        }
                    )
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                    success = false,
                    error = "Signup failed. Please try again."
                ))
            }
        }

        post("/login") {
            // Email/password login: client must authenticate via Firebase and call /auth/google.
            // This stub exists to provide a helpful error instead of 404.
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiResponse<Unit>(
                    success = false,
                    error = "Use Firebase email/password auth on device, then call /auth/google with the token.",
                    code = "USE_FIREBASE_EMAIL_AUTH"
                )
            )
        }

        authenticate("firebase-auth") {
            post("/google") {
                try {
                    val request = call.receive<com.adwatch.backend.domain.request.SignupRequest>()
                    val firebasePrincipal = call.principal<com.adwatch.backend.plugins.FirebaseUserPrincipal>()
                        ?: return@post call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse<Unit>(success = false, error = "Missing Firebase principal")
                        )

                    val firebaseUid = firebasePrincipal.userId
                    val email = firebasePrincipal.email?.lowercase()
                    val country = request.country

                    val existingUser = dbQuery {
                        Users.selectAll().where { Users.authProviderId eq firebaseUid }.singleOrNull()
                            ?: if (email != null) {
                                Users.selectAll().where { Users.email eq email }.singleOrNull()
                            } else null
                    }

                    val userId = existingUser?.get(Users.id)
                        ?: "usr_${UUID.randomUUID().toString().replace("-", "")}"

                    if (existingUser == null) {
                        dbQuery {
                            Users.insert {
                                it[id] = userId
                                it[authProviderId] = firebaseUid
                                it[Users.email] = email
                                it[phone] = null
                                it[Users.country] = country
                                it[status] = "active"
                                it[createdAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }
                        }
                    } else if (existingUser[Users.authProviderId] != firebaseUid) {
                        dbQuery {
                            Users.update({ Users.id eq userId }) {
                                it[authProviderId] = firebaseUid
                                if (email != null) {
                                    it[Users.email] = email
                                }
                                it[updatedAt] = Instant.now()
                            }
                        }
                    }

                    ReferralService.ensureReferralProfile(userId)
                    val googleIp = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                        ?: call.request.local.remoteHost
                    ReferralService.attachReferral(
                        invitedUserId = userId,
                        referralCode = request.referralCode,
                        deviceHash = request.deviceHash,
                        ipHash = com.adwatch.backend.service.Hashing.sha256(googleIp),
                        emulatorFlag = request.emulatorFlag == true
                    )
                    ensureWallet(userId)
                    AuditService.log("user", userId, "user", userId, "google_login")
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = mapOf(
                                "message" to "Google login successful",
                                "userId" to userId,
                                "referralCode" to dbQuery {
                                    com.adwatch.backend.data.table.ReferralProfiles.selectAll()
                                        .where { com.adwatch.backend.data.table.ReferralProfiles.userId eq userId }
                                        .single()[com.adwatch.backend.data.table.ReferralProfiles.referralCode]
                                }
                            )
                        )
                    )
                } catch (_: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "Google login failed")
                    )
                }
            }
        }
    }
}
