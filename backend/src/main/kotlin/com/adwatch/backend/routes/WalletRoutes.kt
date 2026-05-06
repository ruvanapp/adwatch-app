package com.adwatch.backend.routes

import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.*
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.SecurityGuards
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.walletRoutes() {
    authenticate("dev-auth", "firebase-auth") {
        route("/wallet") {
            get {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    val wallet = dbQuery {
                        Wallets.selectAll().where { Wallets.userId eq ctx.userId }.singleOrNull()
                    } ?: return@get call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = "Wallet not found"))

                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf(
                            "wallet" to mapOf(
                                "userId" to wallet[Wallets.userId],
                                "availableCredits" to wallet[Wallets.availableCredits],
                                "pendingCredits" to wallet[Wallets.pendingCredits],
                                "reservedCredits" to wallet[Wallets.reservedCredits],
                                "lifetimeEarnedCredits" to wallet[Wallets.lifetimeEarnedCredits]
                            ),
                            "creditsToUsdRate" to 100.0
                        )
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = e.message))
                }
            }

            get("/ledger") {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val pageSize = (call.parameters["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

                    val entries = dbQuery {
                        LedgerEntries.selectAll().where { LedgerEntries.userId eq ctx.userId }
                            .orderBy(LedgerEntries.createdAt, SortOrder.DESC)
                            .limit(pageSize, ((page - 1) * pageSize).toLong())
                            .map { row ->
                                mapOf(
                                    "id" to row[LedgerEntries.id],
                                    "type" to row[LedgerEntries.type],
                                    "creditsDelta" to row[LedgerEntries.creditsDelta],
                                    "sourceType" to row[LedgerEntries.sourceType],
                                    "status" to row[LedgerEntries.status],
                                    "createdAt" to row[LedgerEntries.createdAt].toString()
                                )
                            }
                    }
                    val total = dbQuery {
                        LedgerEntries.selectAll().where { LedgerEntries.userId eq ctx.userId }.count()
                    }

                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = mapOf(
                            "entries" to entries,
                            "pagination" to mapOf(
                                "page" to page,
                                "pageSize" to pageSize,
                                "totalItems" to total.toInt(),
                                "totalPages" to ((total.toInt() + pageSize - 1) / pageSize)
                            )
                        )
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = e.message))
                }
            }
        }
    }
}
