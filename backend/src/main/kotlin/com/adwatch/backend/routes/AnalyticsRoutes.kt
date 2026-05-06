package com.adwatch.backend.routes

import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.AdWatchSessions
import com.adwatch.backend.data.table.CashoutRequests
import com.adwatch.backend.data.table.LedgerEntries
import com.adwatch.backend.data.table.Users
import com.adwatch.backend.domain.response.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.temporal.ChronoUnit

fun Route.analyticsRoutes() {
    authenticate("admin-auth") {
    route("/analytics") {
        get("/funnels") {
            try {
                val now = Instant.now()
                val since = now.minus(30, ChronoUnit.DAYS)
                val data = dbQuery {
                    val signups = Users.selectAll().where { Users.createdAt greaterEq since }.count()
                    val sessionsStarted = AdWatchSessions.selectAll().where { AdWatchSessions.startedAt greaterEq since }.count()
                    val claimsRewarded = AdWatchSessions.selectAll().where {
                        (AdWatchSessions.completedAt greaterEq since) and (AdWatchSessions.claimStatus eq "rewarded")
                    }.count()
                    val cashoutRequested = CashoutRequests.selectAll().where { CashoutRequests.createdAt greaterEq since }.count()
                    val cashoutPaid = CashoutRequests.selectAll().where {
                        (CashoutRequests.reviewedAt greaterEq since) and (CashoutRequests.status eq "paid")
                    }.count()
                    mapOf(
                        "windowDays" to 30,
                        "signups" to signups,
                        "sessionsStarted" to sessionsStarted,
                        "claimsRewarded" to claimsRewarded,
                        "cashoutRequested" to cashoutRequested,
                        "cashoutPaid" to cashoutPaid
                    )
                }
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = data))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = e.message))
            }
        }

        get("/cohorts/revenue") {
            try {
                val now = Instant.now()
                val buckets = (0..5).map { monthsAgo ->
                    val start = now.minus((monthsAgo + 1).toLong(), ChronoUnit.MONTHS)
                    val end = now.minus(monthsAgo.toLong(), ChronoUnit.MONTHS)
                    val cohortLabel = "${start.toString().substring(0, 7)}"
                    val usersInCohort = dbQuery {
                        Users.selectAll().where { Users.createdAt.between(start, end) }.map { it[Users.id] }
                    }
                    val revenue = if (usersInCohort.isEmpty()) "0.00" else dbQuery {
                        LedgerEntries.selectAll().where {
                            (LedgerEntries.userId inList usersInCohort) and
                                (LedgerEntries.type eq "cashout_paid") and
                                (LedgerEntries.createdAt.between(start, end))
                        }.mapNotNull { it[LedgerEntries.moneyEquivalentUsd] }
                            .fold(java.math.BigDecimal.ZERO) { acc, v -> acc + v }
                            .setScale(2)
                            .toString()
                    }
                    mapOf(
                        "cohort" to cohortLabel,
                        "users" to usersInCohort.size,
                        "cashoutRevenueUsd" to revenue
                    )
                }.reversed()
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = mapOf("cohorts" to buckets)))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = e.message))
            }
        }
    }
    } // authenticate("admin-auth")
}