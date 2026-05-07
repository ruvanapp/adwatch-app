package com.adwatch.backend.service

import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.AdWatchSessions
import com.adwatch.backend.data.table.AuditLogs
import com.adwatch.backend.data.table.CashoutRequests
import com.adwatch.backend.data.table.FeatureFlags
import com.adwatch.backend.data.table.FraudEvents
import com.adwatch.backend.data.table.PayoutTransactions
import com.adwatch.backend.data.table.RewardRules
import com.adwatch.backend.data.table.UserDevices
import com.adwatch.backend.data.table.Users
import com.adwatch.backend.data.table.Wallets
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

const val CREDITS_TO_USD_RATE = 100
const val MIN_CASHOUT_CREDITS = 500
private val json = kotlinx.serialization.json.Json

data class ApiFailure(
    val status: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)

data class UserContext(
    val userId: String,
    val ipHash: String,
    val userAgent: String?
)

data class TrustSignals(
    val integrityVerdict: String,
    val emulatorFlag: Boolean,
    val rootFlag: Boolean,
    val hookFlag: Boolean
)

data class RiskEvaluation(
    val score: Int,
    val blocked: Boolean,
    val holdSuggested: Boolean,
    val reasons: List<String>
)

object SecurityGuards {
    fun extractUserContext(call: ApplicationCall): UserContext {
        // Try dev-auth principal first
        val devPrincipal = call.principal<com.adwatch.backend.plugins.DevUserPrincipal>()
        if (devPrincipal != null) {
            val userId = devPrincipal.userId
            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                ?: call.request.local.remoteHost
            val ipHash = Hashing.sha256(ip)
            val userAgent = call.request.headers["User-Agent"]
            return UserContext(userId = userId, ipHash = ipHash, userAgent = userAgent)
        }

        val firebasePrincipal = call.principal<com.adwatch.backend.plugins.FirebaseUserPrincipal>()
        if (firebasePrincipal != null) {
            val userId = firebasePrincipal.userId
            val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                ?: call.request.local.remoteHost
            val ipHash = Hashing.sha256(ip)
            val userAgent = call.request.headers["User-Agent"]
            return UserContext(userId = userId, ipHash = ipHash, userAgent = userAgent)
        }

        // Fall back to JWT principal if present
        val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
            ?: throw ApiFailure(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Missing auth principal")
        val userId = principal.payload.getClaim("user_id").asString()
            ?: principal.payload.subject
            ?: throw ApiFailure(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Missing user id")
        val ip = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
            ?: call.request.local.remoteHost
        val ipHash = Hashing.sha256(ip)
        val userAgent = call.request.headers["User-Agent"]
        return UserContext(userId = userId, ipHash = ipHash, userAgent = userAgent)
    }

    suspend fun assertUserCanCashout(userId: String) {
        dbQuery {
            val row = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                ?: throw ApiFailure(HttpStatusCode.Forbidden, "USER_NOT_FOUND", "User not found")
            val status = row[Users.status]
            if (status == "banned" || status == "suspended" || status == "under_review") {
                throw ApiFailure(HttpStatusCode.Forbidden, "CASHOUT_BLOCKED", "User is blocked from cashout")
            }
        }
    }

    private val tier1Countries = setOf("US", "GB", "CA", "DE", "AU")
    private val tier2Countries = setOf("IN", "BR", "MX", "ID", "PH")

    private fun countryGroupFor(country: String): String {
        val c = country.uppercase()
        return when {
            tier1Countries.contains(c) -> "TIER1"
            tier2Countries.contains(c) -> "TIER2"
            else -> "TIER3"
        }
    }

    private fun scopeMatches(scope: String?, country: String?): Boolean {
        if (scope.isNullOrBlank() || scope == "*") return true
        if (country.isNullOrBlank()) return false
        val normalizedCountry = country.uppercase()
        val normalizedGroup = countryGroupFor(normalizedCountry)
        val tokens = scope.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        return tokens.any { it == normalizedCountry || it == normalizedGroup || it == "*" || it == "GLOBAL" || it == "ALL" }
    }

    suspend fun assertFeatureEnabled(feature: String, userId: String? = null, country: String? = null) {
        val effectiveCountry = country ?: if (userId != null) {
            dbQuery { Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.country) }
        } else null
        val matchingFlags = dbQuery {
            FeatureFlags.selectAll().where { FeatureFlags.flagName eq feature.lowercase() }.toList()
        }.filter { scopeMatches(it[FeatureFlags.countryScope], effectiveCountry) }
        val enabled = matchingFlags
            .sortedByDescending { if (it[FeatureFlags.countryScope].isNullOrBlank() || it[FeatureFlags.countryScope] == "*") 0 else 1 }
            .firstOrNull()
            ?.get(FeatureFlags.enabled)
        if (enabled == false) {
            throw ApiFailure(HttpStatusCode.Forbidden, "FEATURE_DISABLED", "Feature '$feature' is disabled")
        }
    }
}

object Hashing {
    fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

object TrustService {
    fun fromSignals(
        integrityToken: String?,
        emulatorFlag: Boolean?,
        rootFlag: Boolean?,
        hookFlag: Boolean?
    ): TrustSignals {
        val verdict = when {
            integrityToken.isNullOrBlank() -> "missing"
            integrityToken.lowercase().contains("fail") -> "failed"
            else -> "passed"
        }
        return TrustSignals(
            integrityVerdict = verdict,
            emulatorFlag = emulatorFlag == true,
            rootFlag = rootFlag == true,
            hookFlag = hookFlag == true
        )
    }

    suspend fun recordDeviceSignals(userId: String, deviceHash: String?, trust: TrustSignals): String? {
        if (deviceHash.isNullOrBlank()) return null
        return dbQuery {
            val existing = UserDevices.selectAll().where { UserDevices.deviceHash eq deviceHash }.singleOrNull()
            val now = Instant.now()
            val deviceId = existing?.get(UserDevices.id) ?: "dev_${UUID.randomUUID()}"
            if (existing == null) {
                UserDevices.insert {
                    it[id] = deviceId
                    it[UserDevices.userId] = userId
                    it[UserDevices.deviceHash] = deviceHash
                    it[integrityVerdict] = trust.integrityVerdict
                    it[emulatorFlag] = trust.emulatorFlag
                    it[rootFlag] = trust.rootFlag
                    it[hookFlag] = trust.hookFlag
                    it[firstSeenAt] = now
                    it[lastSeenAt] = now
                }
            } else {
                UserDevices.update({ UserDevices.id eq deviceId }) {
                    it[lastSeenAt] = now
                    it[integrityVerdict] = trust.integrityVerdict
                    it[emulatorFlag] = trust.emulatorFlag
                    it[rootFlag] = trust.rootFlag
                    it[hookFlag] = trust.hookFlag
                }
            }
            deviceId
        }
    }

    suspend fun enforceOneAccountPerDevice(userId: String, deviceHash: String?) {
        if (deviceHash.isNullOrBlank()) return
        dbQuery {
            val usersOnDevice = UserDevices
                .slice(UserDevices.userId)
                .selectAll()
                .where { UserDevices.deviceHash eq deviceHash }
                .map { it[UserDevices.userId] }
                .distinct()
            if (usersOnDevice.isNotEmpty() && usersOnDevice.any { it != userId }) {
                val evidence = buildJsonObject {
                    put("deviceHash", deviceHash.take(16))
                    put("userId", userId)
                    put("existingUsers", usersOnDevice.joinToString(","))
                }
                FraudService.recordFraudEvent(
                    userId = userId,
                    deviceId = null,
                    type = "duplicate_device",
                    severity = "high",
                    evidence = evidence
                )
                throw ApiFailure(
                    HttpStatusCode.Forbidden,
                    "DEVICE_ALREADY_LINKED",
                    "This device is already associated with another account"
                )
            }
        }
    }
}

object VelocityService {
    suspend fun checkClaimVelocity(userId: String, ipHash: String, deviceId: String?): List<String> = dbQuery {
        val reasons = mutableListOf<String>()
        val now = Instant.now()
        val hourAgo = now.minus(1, ChronoUnit.HOURS)
        val dayAgo = now.minus(1, ChronoUnit.DAYS)

        val userHour = AdWatchSessions.selectAll().where {
            (AdWatchSessions.userId eq userId) and (AdWatchSessions.startedAt greaterEq hourAgo)
        }.count()
        if (userHour > 40) reasons.add("user_hourly_limit")

        val userDay = AdWatchSessions.selectAll().where {
            (AdWatchSessions.userId eq userId) and (AdWatchSessions.startedAt greaterEq dayAgo)
        }.count()
        if (userDay > 300) reasons.add("user_daily_limit")

        val ipHour = AdWatchSessions.selectAll().where {
            (AdWatchSessions.ipHash eq ipHash) and (AdWatchSessions.startedAt greaterEq hourAgo)
        }.count()
        if (ipHour > 120) reasons.add("ip_hourly_limit")

        if (!deviceId.isNullOrBlank()) {
            val deviceHour = AdWatchSessions.selectAll().where {
                (AdWatchSessions.deviceId eq deviceId) and (AdWatchSessions.startedAt greaterEq hourAgo)
            }.count()
            if (deviceHour > 60) reasons.add("device_hourly_limit")
        }
        reasons
    }
}

object RewardService {
    private val highTierCountries = setOf("US", "GB", "CA", "DE", "AU")
    private val midTierCountries = setOf("IN", "BR", "MX", "ID", "PH")

    private fun countryGroupFor(country: String): String {
        val c = country.uppercase()
        return when {
            highTierCountries.contains(c) -> "TIER1"
            midTierCountries.contains(c) -> "TIER2"
            else -> "TIER3"
        }
    }

    data class RewardGrantRule(
        val creditsPerCompletion: Int,
        val dailyCap: Int,
        val hourlyCap: Int,
        val cooldownSeconds: Int
    )

    suspend fun creditsForUser(userId: String): Int = dbQuery {
        rewardRuleForUserInline(userId).creditsPerCompletion
    }

    fun rewardRuleForUserInline(userId: String): RewardGrantRule {
        val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
            ?: throw ApiFailure(HttpStatusCode.NotFound, "USER_NOT_FOUND", "User not found")
        val group = countryGroupFor(user[Users.country])
        val rule = RewardRules.selectAll().where {
            (RewardRules.countryGroup eq group) and (RewardRules.active eq true)
        }.singleOrNull()
        return if (rule != null) {
            RewardGrantRule(
                creditsPerCompletion = rule[RewardRules.creditsPerCompletion],
                dailyCap = rule[RewardRules.dailyCap],
                hourlyCap = rule[RewardRules.hourlyCap],
                cooldownSeconds = rule[RewardRules.cooldownSeconds]
            )
        } else {
            RewardGrantRule(
                creditsPerCompletion = defaultCredits(group),
                dailyCap = 300,
                hourlyCap = 40,
                cooldownSeconds = 15
            )
        }
    }

    fun creditsForUserInline(userId: String): Int {
        return rewardRuleForUserInline(userId).creditsPerCompletion
    }

    private fun defaultCredits(group: String): Int = when (group) {
        "TIER1" -> 12
        "TIER2" -> 8
        else -> 5
    }
}

object HeuristicService {
    suspend fun analyzeSessionPattern(userId: String, sessionId: String): List<String> = dbQuery {
        val reasons = mutableListOf<String>()
        val current = AdWatchSessions.selectAll().where { AdWatchSessions.id eq sessionId }.singleOrNull()
            ?: return@dbQuery reasons
        val completedAt = current[AdWatchSessions.completedAt]
        val startedAt = current[AdWatchSessions.startedAt]
        if (completedAt != null) {
            val seconds = java.time.Duration.between(startedAt, completedAt).seconds
            if (seconds < 8) reasons += "session_too_short"
            if (seconds > 7200) reasons += "session_too_long"
        }
        val recent = AdWatchSessions.selectAll().where {
            (AdWatchSessions.userId eq userId) and (AdWatchSessions.completedAt greaterEq Instant.now().minus(20, ChronoUnit.MINUTES))
        }.count()
        if (recent > 25) reasons += "burst_claim_pattern"
        reasons
    }

    fun analyzeSessionPatternInline(userId: String, sessionId: String, now: Instant): List<String> {
        val reasons = mutableListOf<String>()
        val current = AdWatchSessions.selectAll().where { AdWatchSessions.id eq sessionId }.singleOrNull()
            ?: return reasons
        val completedAt = current[AdWatchSessions.completedAt]
        val startedAt = current[AdWatchSessions.startedAt]
        if (completedAt != null) {
            val seconds = java.time.Duration.between(startedAt, completedAt).seconds
            if (seconds < 8) reasons += "session_too_short"
            if (seconds > 7200) reasons += "session_too_long"
        }
        val recent = AdWatchSessions.selectAll().where {
            (AdWatchSessions.userId eq userId) and (AdWatchSessions.completedAt greaterEq now.minus(20, ChronoUnit.MINUTES))
        }.count()
        if (recent > 25) reasons += "burst_claim_pattern"
        return reasons
    }
}

object FraudService {
    suspend fun evaluateRisk(userId: String, trust: TrustSignals, velocityReasons: List<String>): RiskEvaluation {
        var score = 0
        val reasons = mutableListOf<String>()

        if (trust.integrityVerdict != "passed") {
            score += 25
            reasons += "integrity_${trust.integrityVerdict}"
        }
        if (trust.emulatorFlag) {
            score += 35
            reasons += "emulator_detected"
        }
        if (trust.rootFlag) {
            score += 30
            reasons += "root_detected"
        }
        if (trust.hookFlag) {
            score += 45
            reasons += "hook_detected"
        }

        score += velocityReasons.size * 15
        reasons += velocityReasons

        val blocked = score >= 85
        val holdSuggested = !blocked && score >= 55

        if (reasons.isNotEmpty()) {
            recordFraudEvent(
                userId = userId,
                deviceId = null,
                type = "risk_evaluated",
                severity = when {
                    blocked -> "critical"
                    holdSuggested -> "high"
                    else -> "medium"
                },
                evidence = buildJsonObject {
                    put("score", score)
                    put("reasons", reasons.joinToString(","))
                }
            )
        }
        return RiskEvaluation(score, blocked, holdSuggested, reasons)
    }

    suspend fun recordFraudEvent(
        userId: String?,
        deviceId: String?,
        type: String,
        severity: String,
        evidence: JsonObject
    ) {
        dbQuery {
            FraudEvents.insert {
                it[id] = "fraud_${UUID.randomUUID()}"
                it[FraudEvents.userId] = userId
                it[FraudEvents.deviceId] = deviceId
                it[eventType] = type
                it[FraudEvents.severity] = severity
                it[evidenceJson] = json.encodeToString(JsonObject.serializer(), evidence)
                it[createdAt] = Instant.now()
            }
        }
    }
}

object AuditService {
    suspend fun log(
        actorType: String,
        actorId: String?,
        entityType: String,
        entityId: String,
        action: String,
        metadata: JsonObject? = null
    ) {
        dbQuery {
            AuditLogs.insert {
                it[id] = "audit_${UUID.randomUUID()}"
                it[AuditLogs.actorType] = actorType
                it[AuditLogs.actorId] = actorId
                it[AuditLogs.entityType] = entityType
                it[AuditLogs.entityId] = entityId
                it[AuditLogs.action] = action
                it[metadataJson] = metadata?.let { obj -> json.encodeToString(JsonObject.serializer(), obj) }
                it[createdAt] = Instant.now()
            }
        }
    }
}

object Money {
    fun creditsToUsd(credits: Int): BigDecimal =
        BigDecimal(credits).divide(BigDecimal(CREDITS_TO_USD_RATE), 2, RoundingMode.HALF_UP)
}

object PayPalService {
    suspend fun payout(
        config: ApplicationConfig,
        requestId: String,
        paypalEmail: String,
        amount: BigDecimal
    ): Triple<String, String, String> {
        val mode = config.propertyOrNull("paypal.mode")?.getString() ?: "sandbox"
        val batchId = "batch_${mode}_${UUID.randomUUID()}"
        val itemId = "item_${UUID.randomUUID()}"
        val rawRef = "sandbox://payout/$requestId/$paypalEmail/${amount.setScale(2, RoundingMode.HALF_UP)}"
        return Triple(batchId, itemId, rawRef)
    }
}

suspend fun ensureWallet(userId: String) {
    dbQuery {
        val existing = Wallets.selectAll().where { Wallets.userId eq userId }.singleOrNull()
        if (existing == null) {
            Wallets.insert {
                it[Wallets.userId] = userId
                it[availableCredits] = 0
                it[pendingCredits] = 0
                it[reservedCredits] = 0
                it[lifetimeEarnedCredits] = 0
                it[updatedAt] = Instant.now()
            }
        }
    }
}