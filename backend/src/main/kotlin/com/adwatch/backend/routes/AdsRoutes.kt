package com.adwatch.backend.routes

import com.adwatch.backend.domain.request.StartAdSessionRequest
import com.adwatch.backend.domain.request.ClaimRewardRequest
import com.adwatch.backend.domain.response.ApiResponse
import com.adwatch.backend.service.SecurityGuards
import com.adwatch.backend.service.TrustService
import com.adwatch.backend.service.VelocityService
import com.adwatch.backend.service.FraudService
import com.adwatch.backend.service.ApiFailure
import com.adwatch.backend.service.AuditService
import com.adwatch.backend.service.HeuristicService
import com.adwatch.backend.service.RewardService
import com.adwatch.backend.service.ReferralService
import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.AdWatchSessions
import com.adwatch.backend.data.table.LedgerEntries
import com.adwatch.backend.data.table.Wallets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Serializable
private data class StartSessionData(val message: String, val sessionId: String, val riskScore: Int, val holdSuggested: Boolean)

@Serializable
private data class ClaimRewardData(val message: String, val sessionId: String, val creditsEarned: Int)

fun Route.adsRoutes() {
    authenticate("dev-auth", "firebase-auth") {
        route("/ads") {
            post("/session/start") {
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    SecurityGuards.assertFeatureEnabled("ads", userId = ctx.userId)
                    val request = call.receive<StartAdSessionRequest>()
                    val trust = TrustService.fromSignals(
                        integrityToken = request.integrityToken,
                        emulatorFlag = request.emulatorFlag,
                        rootFlag = request.rootFlag,
                        hookFlag = request.hookFlag
                    )
                    val deviceId = TrustService.recordDeviceSignals(ctx.userId, request.deviceHash, trust) ?: request.deviceId
                    val velocityReasons = VelocityService.checkClaimVelocity(ctx.userId, ctx.ipHash, deviceId)
                    val risk = FraudService.evaluateRisk(ctx.userId, trust, velocityReasons)
                    if (risk.blocked) {
                        throw ApiFailure(HttpStatusCode.Forbidden, "RISK_BLOCKED", "Risk threshold exceeded")
                    }

                    val sessionId = "ads_${UUID.randomUUID()}"
                    dbQuery {
                        AdWatchSessions.insert {
                            it[id] = sessionId
                            it[userId] = ctx.userId
                            it[adNetwork] = request.adNetwork
                            it[adUnitId] = request.adUnitId
                            it[placement] = request.placement
                            it[startedAt] = Instant.now()
                            it[completedAt] = null
                            it[sdkRewardAmount] = null
                            it[sdkRewardType] = null
                            it[claimStatus] = "pending"
                            it[AdWatchSessions.deviceId] = deviceId
                            it[ipHash] = ctx.ipHash
                        }
                    }
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = StartSessionData(
                            message = "Session started",
                            sessionId = sessionId,
                            riskScore = risk.score,
                            holdSuggested = risk.holdSuggested
                        )
                    ))
                } catch (e: ApiFailure) {
                    call.respond(e.status, ApiResponse<Unit>(success = false, error = e.message, code = e.code))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = "Failed to start ad session"
                    ))
                }
            }
            
            post("/session/claim-reward") {
                val request = call.receive<ClaimRewardRequest>()
                try {
                    val ctx = SecurityGuards.extractUserContext(call)
                    SecurityGuards.assertFeatureEnabled("ads", userId = ctx.userId)
                    val now = Instant.now()
                    var creditsEarned = 0
                    var heuristics: List<String> = emptyList()
                    dbQuery {
                        val session = AdWatchSessions.selectAll().where {
                            (AdWatchSessions.id eq request.sessionId) and (AdWatchSessions.userId eq ctx.userId)
                        }.singleOrNull() ?: throw ApiFailure(
                            HttpStatusCode.NotFound, "SESSION_NOT_FOUND", "Ad session not found"
                        )

                        val existingStatus = session[AdWatchSessions.claimStatus]
                        val existingKey = session[AdWatchSessions.claimIdempotencyKey]
                        if (existingStatus == "rewarded") {
                            if (!request.idempotencyKey.isNullOrBlank() && existingKey == request.idempotencyKey) {
                                return@dbQuery
                            }
                            throw ApiFailure(
                                HttpStatusCode.Conflict,
                                "DUPLICATE_CLAIM_REJECTED",
                                "Duplicate claim attempt rejected"
                            )
                        }
                        val rule = RewardService.rewardRuleForUserInline(ctx.userId)
                        val isGoogleTestAd = session[AdWatchSessions.adNetwork].equals("admob", ignoreCase = true) &&
                            session[AdWatchSessions.adUnitId].startsWith("ca-app-pub-3940256099942544/")
                        val cooldownWindow = now.minusSeconds(rule.cooldownSeconds.toLong())
                        val dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                        val hourStart = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                        val userSessions = AdWatchSessions
                            .selectAll()
                            .where { AdWatchSessions.userId eq ctx.userId }
                            .orderBy(AdWatchSessions.completedAt, SortOrder.DESC)
                            .toList()
                        if (!isGoogleTestAd) {
                            val lastCompleted = userSessions.firstOrNull {
                                it[AdWatchSessions.claimStatus] == "rewarded" && it[AdWatchSessions.completedAt] != null
                            }?.get(AdWatchSessions.completedAt)
                            if (lastCompleted != null && lastCompleted.isAfter(cooldownWindow)) {
                                throw ApiFailure(HttpStatusCode.TooManyRequests, "COOLDOWN_ACTIVE", "Reward cooldown active")
                            }
                            val dailyRewardedCount = userSessions.count {
                                it[AdWatchSessions.claimStatus] == "rewarded" &&
                                    (it[AdWatchSessions.completedAt]?.isAfter(dayStart) == true || it[AdWatchSessions.completedAt] == dayStart)
                            }
                            if (dailyRewardedCount >= rule.dailyCap) {
                                throw ApiFailure(HttpStatusCode.TooManyRequests, "DAILY_CAP_REACHED", "Daily reward cap reached")
                            }
                            val hourlyRewardedCount = userSessions.count {
                                it[AdWatchSessions.claimStatus] == "rewarded" &&
                                    (it[AdWatchSessions.completedAt]?.isAfter(hourStart) == true || it[AdWatchSessions.completedAt] == hourStart)
                            }
                            if (hourlyRewardedCount >= rule.hourlyCap) {
                                throw ApiFailure(HttpStatusCode.TooManyRequests, "HOURLY_CAP_REACHED", "Hourly reward cap reached")
                            }
                        }

                        AdWatchSessions.update({ AdWatchSessions.id eq request.sessionId }) {
                            it[completedAt] = now
                            it[sdkRewardAmount] = request.sdkRewardAmount
                            it[sdkRewardType] = request.sdkRewardType
                            it[claimStatus] = "rewarded"
                            it[claimIdempotencyKey] = request.idempotencyKey
                        }

                        heuristics = HeuristicService.analyzeSessionPatternInline(ctx.userId, request.sessionId, now)
                        val wallet = Wallets.selectAll().where { Wallets.userId eq ctx.userId }.singleOrNull()
                            ?: run {
                                Wallets.insert {
                                    it[userId] = ctx.userId
                                    it[availableCredits] = 0
                                    it[pendingCredits] = 0
                                    it[reservedCredits] = 0
                                    it[lifetimeEarnedCredits] = 0
                                    it[updatedAt] = now
                                }
                                Wallets.selectAll().where { Wallets.userId eq ctx.userId }.single()
                            }
                        creditsEarned = rule.creditsPerCompletion
                        Wallets.update({ Wallets.userId eq ctx.userId }) {
                            it[availableCredits] = wallet[Wallets.availableCredits] + creditsEarned
                            it[lifetimeEarnedCredits] = wallet[Wallets.lifetimeEarnedCredits] + creditsEarned
                            it[updatedAt] = now
                        }
                        LedgerEntries.insert {
                            it[id] = "led_${UUID.randomUUID()}"
                            it[userId] = ctx.userId
                            it[type] = "EARN"
                            it[creditsDelta] = creditsEarned
                            it[moneyEquivalentUsd] = BigDecimal(creditsEarned)
                                .divide(BigDecimal(100), 4, java.math.RoundingMode.HALF_UP)
                            it[sourceType] = "ad_watch_session"
                            it[sourceId] = request.sessionId
                            it[status] = "confirmed"
                            it[createdAt] = now
                        }
                    }
                    ReferralService.onUserEarned(
                        invitedUserId = ctx.userId,
                        sourceSessionId = request.sessionId,
                        earnedCredits = creditsEarned
                    )
                    if (heuristics.isNotEmpty()) {
                        FraudService.recordFraudEvent(
                            userId = ctx.userId,
                            deviceId = request.deviceId,
                            type = "session_pattern_anomaly",
                            severity = "medium",
                            evidence = buildJsonObject {
                                put("sessionId", request.sessionId)
                                put("reasons", heuristics.joinToString(","))
                            }
                        )
                    }
                    AuditService.log(
                        actorType = "user",
                        actorId = ctx.userId,
                        entityType = "ad_watch_session",
                        entityId = request.sessionId,
                        action = "reward_claimed",
                        metadata = buildJsonObject {
                            put("creditsEarned", creditsEarned)
                        }
                    )
                    call.respond(HttpStatusCode.OK, ApiResponse(
                        success = true,
                        data = ClaimRewardData(
                            message = "Reward claim accepted",
                            sessionId = request.sessionId,
                            creditsEarned = creditsEarned
                        )
                    ))
                } catch (e: ApiFailure) {
                    if (e.code == "DUPLICATE_CLAIM_REJECTED") {
                        FraudService.recordFraudEvent(
                            userId = null,
                            deviceId = request.deviceId,
                            type = "duplicate_claim_attempt",
                            severity = "medium",
                            evidence = buildJsonObject {
                                put("sessionId", request.sessionId)
                                put("idempotencyKey", request.idempotencyKey ?: "")
                            }
                        )
                    }
                    call.respond(e.status, ApiResponse<Unit>(success = false, error = e.message, code = e.code))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(
                        success = false,
                        error = "Failed to claim reward"
                    ))
                }
            }
        }
    }
}
