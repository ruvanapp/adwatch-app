package com.adwatch.backend.routes

import com.adwatch.backend.domain.request.ReviewCashoutRequest
import com.adwatch.backend.domain.request.UpsertRewardRuleRequest
import com.adwatch.backend.domain.request.UpdateFeatureFlagRequest
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.*
import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.CashoutRequests
import com.adwatch.backend.data.table.FeatureFlags
import com.adwatch.backend.data.table.FraudEvents
import com.adwatch.backend.data.table.PayoutTransactions
import com.adwatch.backend.data.table.RewardRules
import com.adwatch.backend.data.table.Wallets
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.adminRoutes() {
    authenticate("admin-auth") {
    route("/admin") {
        route("/users") {
            get {
                try {
                    // TODO: List users with filters
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("message" to "Admin users list - implementation pending")
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to fetch users"
                    ))
                }
            }
            
            get("/{id}") {
                try {
                    val userId = call.parameters["id"]
                    // TODO: Get user details with wallet, fraud events, etc.
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("message" to "Admin user detail - implementation pending")
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to fetch user"
                    ))
                }
            }
            
            post("/{id}/status") {
                try {
                    val userId = call.parameters["id"]
                    // TODO: Update user status (ban, suspend, etc.)
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("message" to "Update user status - implementation pending")
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to update user status"
                    ))
                }
            }
        }
        
        route("/cashouts") {
            get {
                try {
                    val queue = dbQuery {
                        CashoutRequests.selectAll()
                            .where { CashoutRequests.status eq "pending" }
                            .orderBy(CashoutRequests.createdAt, SortOrder.ASC)
                            .map {
                                mapOf(
                                    "id" to it[CashoutRequests.id],
                                    "userId" to it[CashoutRequests.userId],
                                    "paypalEmail" to it[CashoutRequests.paypalEmail],
                                    "requestedCredits" to it[CashoutRequests.requestedCredits],
                                    "requestedAmountUsd" to it[CashoutRequests.requestedAmountUsd].toString(),
                                    "createdAt" to it[CashoutRequests.createdAt].toString()
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("queue" to queue)
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to fetch cashouts"
                    ))
                }
            }
            
            post("/{id}/approve") {
                try {
                    val cashoutId = call.parameters["id"]
                    val review = call.receive<ReviewCashoutRequest>()
                    if (cashoutId.isNullOrBlank()) {
                        throw ApiFailure(HttpStatusCode.BadRequest, "INVALID_CASHOUT_ID", "Missing cashout id")
                    }
                    if (review.hold) {
                        dbQuery {
                            CashoutRequests.update({ CashoutRequests.id eq cashoutId }) {
                                it[status] = "hold"
                                it[reviewerId] = "admin"
                                it[reviewNotes] = review.notes
                                it[reviewedAt] = Instant.now()
                            }
                        }
                        AuditService.log(
                            actorType = "admin",
                            actorId = "admin",
                            entityType = "cashout_request",
                            entityId = cashoutId,
                            action = "hold",
                            metadata = buildJsonObject { put("notes", review.notes ?: "") }
                        )
                        call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("message" to "Cashout moved to hold")))
                        return@post
                    }

                    val payoutResult = dbQuery {
                        val cashout = CashoutRequests.selectAll().where { CashoutRequests.id eq cashoutId }.singleOrNull()
                            ?: throw ApiFailure(HttpStatusCode.NotFound, "CASHOUT_NOT_FOUND", "Cashout not found")
                        val status = cashout[CashoutRequests.status]
                        if (status != "pending" && status != "hold") {
                            throw ApiFailure(HttpStatusCode.Conflict, "INVALID_STATE", "Only pending/hold cashouts can be approved")
                        }
                        val userId = cashout[CashoutRequests.userId]
                        SecurityGuards.assertUserCanCashout(userId)
                        val paypalEmail = cashout[CashoutRequests.paypalEmail]
                        val amount = cashout[CashoutRequests.requestedAmountUsd]
                        val credits = cashout[CashoutRequests.requestedCredits]
                        val wallet = Wallets.selectAll().where { Wallets.userId eq userId }.single()
                        val reserved = wallet[Wallets.reservedCredits]
                        if (reserved < credits) {
                            throw ApiFailure(HttpStatusCode.Conflict, "RESERVE_MISMATCH", "Reserved credits mismatch")
                        }

                        CashoutRequests.update({ CashoutRequests.id eq cashoutId }) {
                            it[CashoutRequests.status] = "approved"
                            it[reviewerId] = "admin"
                            it[reviewNotes] = review.notes
                            it[reviewedAt] = Instant.now()
                        }

                        val (batchId, itemId, rawRef) = PayPalService.payout(
                            call.application.environment.config,
                            cashoutId,
                            paypalEmail,
                            amount
                        )

                        PayoutTransactions.insert {
                            it[id] = "po_${UUID.randomUUID()}"
                            it[cashoutRequestId] = cashoutId
                            it[provider] = "paypal"
                            it[providerBatchId] = batchId
                            it[providerItemId] = itemId
                            it[PayoutTransactions.amount] = amount
                            it[currency] = "USD"
                            it[PayoutTransactions.status] = "success"
                            it[rawResponseRef] = rawRef
                            it[createdAt] = Instant.now()
                            it[completedAt] = Instant.now()
                        }

                        Wallets.update({ Wallets.userId eq userId }) {
                            it[reservedCredits] = reserved - credits
                            it[updatedAt] = Instant.now()
                        }

                        CashoutRequests.update({ CashoutRequests.id eq cashoutId }) {
                            it[CashoutRequests.status] = "paid"
                        }
                        Triple(batchId, itemId, userId)
                    }

                    AuditService.log("admin", "admin", "cashout_request", cashoutId, "approved")
                    AuditService.log(
                        "system",
                        "paypal",
                        "cashout_request",
                        cashoutId,
                        "paid",
                        buildJsonObject {
                            put("batchId", payoutResult.first)
                            put("itemId", payoutResult.second)
                            put("userId", payoutResult.third)
                        }
                    )
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("message" to "Cashout approved and paid in sandbox", "cashoutId" to cashoutId)
                    ))
                } catch (e: ApiFailure) {
                    call.respond(e.status, ApiResponse<Unit>(success = false, error = e.message, code = e.code))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to approve cashout"
                    ))
                }
            }
            
            post("/{id}/reject") {
                try {
                    val cashoutId = call.parameters["id"]
                    val review = call.receive<ReviewCashoutRequest>()
                    if (cashoutId.isNullOrBlank()) {
                        throw ApiFailure(HttpStatusCode.BadRequest, "INVALID_CASHOUT_ID", "Missing cashout id")
                    }
                    dbQuery {
                        val cashout = CashoutRequests.selectAll().where { CashoutRequests.id eq cashoutId }.singleOrNull()
                            ?: throw ApiFailure(HttpStatusCode.NotFound, "CASHOUT_NOT_FOUND", "Cashout not found")
                        val status = cashout[CashoutRequests.status]
                        if (status != "pending" && status != "hold") {
                            throw ApiFailure(HttpStatusCode.Conflict, "INVALID_STATE", "Only pending/hold cashouts can be rejected")
                        }
                        val userId = cashout[CashoutRequests.userId]
                        val credits = cashout[CashoutRequests.requestedCredits]
                        val wallet = Wallets.selectAll().where { Wallets.userId eq userId }.single()
                        Wallets.update({ Wallets.userId eq userId }) {
                            it[availableCredits] = wallet[Wallets.availableCredits] + credits
                            it[reservedCredits] = (wallet[Wallets.reservedCredits] - credits).coerceAtLeast(0)
                            it[updatedAt] = Instant.now()
                        }
                        CashoutRequests.update({ CashoutRequests.id eq cashoutId }) {
                            it[CashoutRequests.status] = "rejected"
                            it[reviewerId] = "admin"
                            it[reviewNotes] = review.notes
                            it[reviewedAt] = Instant.now()
                        }
                    }
                    AuditService.log(
                        "admin",
                        "admin",
                        "cashout_request",
                        cashoutId,
                        "rejected",
                        buildJsonObject { put("notes", review.notes ?: "") }
                    )
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("message" to "Cashout rejected", "cashoutId" to cashoutId)
                    ))
                } catch (e: ApiFailure) {
                    call.respond(e.status, ApiResponse<Unit>(success = false, error = e.message, code = e.code))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to reject cashout"
                    ))
                }
            }
        }
        
        route("/fraud-events") {
            get {
                try {
                    val events = dbQuery {
                        FraudEvents.selectAll()
                            .orderBy(FraudEvents.createdAt, SortOrder.DESC)
                            .limit(200)
                            .map {
                                mapOf(
                                    "id" to it[FraudEvents.id],
                                    "userId" to it[FraudEvents.userId],
                                    "deviceId" to it[FraudEvents.deviceId],
                                    "eventType" to it[FraudEvents.eventType],
                                    "severity" to it[FraudEvents.severity],
                                    "createdAt" to it[FraudEvents.createdAt].toString()
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("events" to events)
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to fetch fraud events"
                    ))
                }
            }
        }
        
        route("/reward-rules") {
            get {
                try {
                    val rules = dbQuery {
                        RewardRules.selectAll()
                            .orderBy(RewardRules.countryGroup, SortOrder.ASC)
                            .map {
                                mapOf(
                                    "id" to it[RewardRules.id],
                                    "countryGroup" to it[RewardRules.countryGroup],
                                    "creditsPerCompletion" to it[RewardRules.creditsPerCompletion],
                                    "dailyCap" to it[RewardRules.dailyCap],
                                    "hourlyCap" to it[RewardRules.hourlyCap],
                                    "cooldownSeconds" to it[RewardRules.cooldownSeconds],
                                    "active" to it[RewardRules.active]
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("rules" to rules)))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to fetch reward rules"
                    ))
                }
            }
            post {
                try {
                    val payload = call.receive<UpsertRewardRuleRequest>()
                    val countryGroup = payload.countryGroup.uppercase()
                    val creditsPerCompletion = payload.creditsPerCompletion
                    val dailyCap = payload.dailyCap
                    val hourlyCap = payload.hourlyCap
                    val cooldownSeconds = payload.cooldownSeconds
                    val active = payload.active
                    dbQuery {
                        val existing = RewardRules.selectAll().where { RewardRules.countryGroup eq countryGroup }.singleOrNull()
                        if (existing == null) {
                            RewardRules.insert {
                                it[id] = "rr_${UUID.randomUUID()}"
                                it[RewardRules.countryGroup] = countryGroup
                                it[RewardRules.creditsPerCompletion] = creditsPerCompletion
                                it[RewardRules.dailyCap] = dailyCap
                                it[RewardRules.hourlyCap] = hourlyCap
                                it[RewardRules.cooldownSeconds] = cooldownSeconds
                                it[RewardRules.active] = active
                                it[createdAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }
                        } else {
                            RewardRules.update({ RewardRules.countryGroup eq countryGroup }) {
                                it[RewardRules.creditsPerCompletion] = creditsPerCompletion
                                it[RewardRules.dailyCap] = dailyCap
                                it[RewardRules.hourlyCap] = hourlyCap
                                it[RewardRules.cooldownSeconds] = cooldownSeconds
                                it[RewardRules.active] = active
                                it[updatedAt] = Instant.now()
                            }
                        }
                    }
                    call.respond(HttpStatusCode.Created, ApiResponse(
                        success = true,
                        data = mapOf(
                            "message" to "Reward rule upserted",
                            "countryGroup" to countryGroup,
                            "creditsPerCompletion" to creditsPerCompletion
                        )
                    ))
                } catch (e: ApiFailure) {
                    call.respond(e.status, ApiResponse<Unit>(
                        success = false,
                        error = e.message,
                        code = e.code
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to update reward rules"
                    ))
                }
            }
        }

        route("/feature-flags") {
            get {
                try {
                    val flags = dbQuery {
                        FeatureFlags.selectAll().orderBy(FeatureFlags.flagName, SortOrder.ASC).map {
                            mapOf(
                                "flagName" to it[FeatureFlags.flagName],
                                "enabled" to it[FeatureFlags.enabled],
                                "countryScope" to it[FeatureFlags.countryScope],
                                "updatedBy" to it[FeatureFlags.updatedBy],
                                "updatedAt" to it[FeatureFlags.updatedAt].toString()
                            )
                        }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("flags" to flags)))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = e.message))
                }
            }
            post("/{key}") {
                try {
                    val key = call.parameters["key"]?.trim()?.lowercase()
                        ?: throw ApiFailure(HttpStatusCode.BadRequest, "INVALID_FLAG_KEY", "Missing flag key")
                    val payload = call.receive<UpdateFeatureFlagRequest>()
                    val enabled = payload.enabled
                    val countryScope = payload.countryScope?.trim()?.ifBlank { null }
                    val updatedBy = payload.updatedBy ?: "admin"
                    dbQuery {
                        val existing = FeatureFlags.selectAll().where { FeatureFlags.flagName eq key }.singleOrNull()
                        if (existing == null) {
                            FeatureFlags.insert {
                                it[FeatureFlags.flagName] = key
                                it[FeatureFlags.enabled] = enabled
                                it[FeatureFlags.countryScope] = countryScope
                                it[FeatureFlags.updatedBy] = updatedBy
                                it[FeatureFlags.updatedAt] = Instant.now()
                            }
                        } else {
                            FeatureFlags.update({ FeatureFlags.flagName eq key }) {
                                it[FeatureFlags.enabled] = enabled
                                it[FeatureFlags.countryScope] = countryScope
                                it[FeatureFlags.updatedBy] = updatedBy
                                it[FeatureFlags.updatedAt] = Instant.now()
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("key" to key, "enabled" to enabled)))
                } catch (e: ApiFailure) {
                    call.respond(e.status, ApiResponse<Unit>(success = false, error = e.message, code = e.code))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                }
            }
        }
    }
    } // authenticate("admin-auth")
}
