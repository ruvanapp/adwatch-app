package com.adwatch.backend.routes

import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.*
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.SecurityGuards
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.temporal.ChronoUnit
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class SimpleUser(val id: String, val email: String?, val country: String, val status: String)

@Serializable
private data class SimpleWallet(val userId: String, val availableCredits: Int, val pendingCredits: Int, val reservedCredits: Int, val lifetimeEarnedCredits: Int)

@Serializable
private data class SimpleDailyProgress(val adsWatchedToday: Int, val dailyLimit: Int, val creditsEarnedToday: Int)

@Serializable
private data class SimpleHomeResponse(
    val user: SimpleUser,
    val wallet: SimpleWallet,
    val dailyProgress: SimpleDailyProgress,
    val canWatchAd: Boolean,
    val nextAdAvailableIn: Int?
)

fun Route.userRoutes() {
    authenticate("dev-auth", "firebase-auth") {
        get("/me") {
            try {
                val ctx = SecurityGuards.extractUserContext(call)
                val user = dbQuery {
                    Users.selectAll().where { Users.id eq ctx.userId }.singleOrNull()
                } ?: return@get call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = "User not found"))

                val wallet = dbQuery {
                    Wallets.selectAll().where { Wallets.userId eq ctx.userId }.singleOrNull()
                }

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = mapOf(
                        "user" to mapOf(
                            "id" to user[Users.id],
                            "email" to user[Users.email],
                            "country" to user[Users.country],
                            "status" to user[Users.status]
                        ),
                        "wallet" to if (wallet != null) mapOf(
                            "availableCredits" to wallet[Wallets.availableCredits],
                            "pendingCredits" to wallet[Wallets.pendingCredits],
                            "reservedCredits" to wallet[Wallets.reservedCredits],
                            "lifetimeEarnedCredits" to wallet[Wallets.lifetimeEarnedCredits]
                        ) else null
                    )
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Failed to load profile"))
            }
        }

        get("/home") {
            try {
                val ctx = SecurityGuards.extractUserContext(call)
                val user = dbQuery {
                    Users.selectAll().where { Users.id eq ctx.userId }.singleOrNull()
                } ?: return@get call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = "User not found"))

                val wallet = dbQuery {
                    Wallets.selectAll().where { Wallets.userId eq ctx.userId }.singleOrNull()
                }

                val now = Instant.now()
                val dayStart = now.truncatedTo(ChronoUnit.DAYS)

                val adsToday = dbQuery {
                    AdWatchSessions.selectAll().where {
                        (AdWatchSessions.userId eq ctx.userId) and
                        (AdWatchSessions.startedAt greaterEq dayStart) and
                        (AdWatchSessions.claimStatus eq "rewarded")
                    }.count().toInt()
                }

                val creditsToday = dbQuery {
                    LedgerEntries.selectAll().where {
                        (LedgerEntries.userId eq ctx.userId) and
                        (LedgerEntries.type eq "EARN") and
                        (LedgerEntries.createdAt greaterEq dayStart)
                    }.sumOf { it[LedgerEntries.creditsDelta] }
                }

                val country = user[Users.country]
                val rule = dbQuery {
                    RewardRules.selectAll().where {
                        (RewardRules.countryGroup eq com.adwatch.backend.service.SecurityGuards.let {
                            when {
                                country.uppercase() in setOf("US", "GB", "CA", "DE", "AU") -> "TIER1"
                                country.uppercase() in setOf("IN", "BR", "MX", "ID", "PH") -> "TIER2"
                                else -> "TIER3"
                            }
                        }) and (RewardRules.active eq true)
                    }.singleOrNull()
                }
                val dailyLimit = rule?.get(RewardRules.dailyCap) ?: 30
                val canWatch = adsToday < dailyLimit && user[Users.status] == "active"

                val homeResponse = SimpleHomeResponse(
                    user = SimpleUser(
                        id = user[Users.id],
                        email = user[Users.email],
                        country = country,
                        status = user[Users.status]
                    ),
                    wallet = SimpleWallet(
                        userId = ctx.userId,
                        availableCredits = wallet?.get(Wallets.availableCredits) ?: 0,
                        pendingCredits = wallet?.get(Wallets.pendingCredits) ?: 0,
                        reservedCredits = wallet?.get(Wallets.reservedCredits) ?: 0,
                        lifetimeEarnedCredits = wallet?.get(Wallets.lifetimeEarnedCredits) ?: 0
                    ),
                    dailyProgress = SimpleDailyProgress(
                        adsWatchedToday = adsToday,
                        dailyLimit = dailyLimit,
                        creditsEarnedToday = creditsToday
                    ),
                    canWatchAd = canWatch,
                    nextAdAvailableIn = null
                )

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = homeResponse
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Failed to load home data"))
            }
        }
    }
}
