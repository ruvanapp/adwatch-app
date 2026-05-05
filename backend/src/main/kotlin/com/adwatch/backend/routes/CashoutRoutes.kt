package com.adwatch.backend.routes

import com.adwatch.backend.domain.request.CashoutRequestDto
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.*
import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.CashoutRequests
import com.adwatch.backend.data.table.Wallets
import com.adwatch.backend.domain.model.CashoutStatus
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

fun Route.cashoutRoutes() {
    authenticate("dev-auth", "firebase-auth") {
        route("/cashouts") {
            post("/request") {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    SecurityGuards.assertFeatureEnabled("cashout", userId = ctx.userId)
                    SecurityGuards.assertUserCanCashout(ctx.userId)
                    val request = call.receive<CashoutRequestDto>()
                    if (!request.paypalEmail.contains("@")) {
                        throw ApiFailure(HttpStatusCode.BadRequest, "INVALID_PAYPAL_EMAIL", "Invalid PayPal email")
                    }
                    if (request.requestedCredits < MIN_CASHOUT_CREDITS) {
                        throw ApiFailure(
                            HttpStatusCode.BadRequest,
                            "MIN_THRESHOLD_NOT_MET",
                            "Minimum cashout is $MIN_CASHOUT_CREDITS credits"
                        )
                    }

                    val created = dbQuery {
                        ensureWallet(ctx.userId)
                        val wallet = Wallets.selectAll().where { Wallets.userId eq ctx.userId }.single()
                        val available = wallet[Wallets.availableCredits]
                        if (available < request.requestedCredits) {
                            throw ApiFailure(HttpStatusCode.BadRequest, "INSUFFICIENT_CREDITS", "Insufficient credits")
                        }
                        Wallets.update({ Wallets.userId eq ctx.userId }) {
                            it[availableCredits] = available - request.requestedCredits
                            it[reservedCredits] = wallet[Wallets.reservedCredits] + request.requestedCredits
                            it[updatedAt] = Instant.now()
                        }
                        val id = "co_${UUID.randomUUID()}"
                        val amount = Money.creditsToUsd(request.requestedCredits)
                        CashoutRequests.insert {
                            it[CashoutRequests.id] = id
                            it[userId] = ctx.userId
                            it[paypalEmail] = request.paypalEmail.lowercase()
                            it[requestedCredits] = request.requestedCredits
                            it[requestedAmountUsd] = amount
                            it[status] = CashoutStatus.PENDING.name.lowercase()
                            it[reviewerId] = null
                            it[reviewNotes] = null
                            it[createdAt] = Instant.now()
                            it[reviewedAt] = null
                        }
                        id
                    }
                    AuditService.log(
                        actorType = "user",
                        actorId = ctx.userId,
                        entityType = "cashout_request",
                        entityId = created,
                        action = "requested",
                        metadata = buildJsonObject {
                            put("requestedCredits", request.requestedCredits)
                            put("paypalEmail", request.paypalEmail.lowercase())
                        }
                    )
                    call.respond(HttpStatusCode.Created, ApiResponse(
                        success = true,
                        data = mapOf("message" to "Cashout request created", "cashoutId" to created)
                    ))
                } catch (e: ApiFailure) {
                    call.respond(e.status, ApiResponse<Unit>(success = false, error = e.message, code = e.code))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to create cashout request"
                    ))
                }
            }
            
            get {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    val requests = dbQuery {
                        CashoutRequests.selectAll()
                            .where { CashoutRequests.userId eq ctx.userId }
                            .orderBy(CashoutRequests.createdAt, SortOrder.DESC)
                            .map {
                                mapOf(
                                    "id" to it[CashoutRequests.id],
                                    "paypalEmail" to it[CashoutRequests.paypalEmail],
                                    "requestedCredits" to it[CashoutRequests.requestedCredits],
                                    "requestedAmountUsd" to it[CashoutRequests.requestedAmountUsd].toString(),
                                    "status" to it[CashoutRequests.status],
                                    "createdAt" to it[CashoutRequests.createdAt].toString()
                                )
                            }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf("cashouts" to requests)
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(
                        success = false,
                        error = e.message ?: "Failed to fetch cashouts"
                    ))
                }
            }
        }
    }
}
