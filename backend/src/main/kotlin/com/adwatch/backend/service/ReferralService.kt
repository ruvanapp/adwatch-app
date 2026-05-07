package com.adwatch.backend.service

import com.adwatch.backend.config.DatabaseFactory.dbQuery
import com.adwatch.backend.data.table.AdWatchSessions
import com.adwatch.backend.data.table.LedgerEntries
import com.adwatch.backend.data.table.ReferralCommissionLogs
import com.adwatch.backend.data.table.ReferralProfiles
import com.adwatch.backend.data.table.ReferralProgramSettings
import com.adwatch.backend.data.table.ReferralRelationships
import com.adwatch.backend.data.table.UserDevices
import com.adwatch.backend.data.table.UserNotifications
import com.adwatch.backend.data.table.Users
import com.adwatch.backend.data.table.Wallets
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class ReferralProgramSettingsData(
    val rewardAmountCredits: Int,
    val percentageCommission: Int,
    val minimumActivityCredits: Int,
    val dailyReferralLimit: Int,
    val fraudBlockSameDevice: Boolean,
    val fraudBlockSameIp: Boolean,
    val fraudBlockEmulator: Boolean,
    val fraudBlockMultipleAccounts: Boolean,
    val active: Boolean
)

@Serializable
data class ReferralInviteSummary(
    val referralCode: String,
    val referralLink: String,
    val currentReferralEarnings: Int,
    val invitedUsersCount: Int,
    val activeReferralsCount: Int,
    val pendingReferralsCount: Int,
    val approvedReferralsCount: Int,
    val rejectedReferralsCount: Int,
    val totalEarnings: Int
)

@Serializable
data class ReferralRelationshipSummary(
    val relationshipId: String,
    val invitedUserId: String,
    val invitedEmail: String?,
    val status: String,
    val source: String,
    val fraudReasons: String?,
    val invitedAt: String,
    val approvedAt: String?,
    val rejectedAt: String?,
    val totalCommissionEarned: Int
)

@Serializable
data class AdminReferralOverview(
    val totalReferrals: Int,
    val pendingReferrals: Int,
    val approvedReferrals: Int,
    val rejectedReferrals: Int,
    val totalCommissionCredits: Int
)

@Serializable
data class TopInviterSummary(
    val userId: String,
    val email: String?,
    val approvedReferrals: Int,
    val totalCommissionCredits: Int
)

object ReferralService {
    private const val SETTINGS_ID = "default"

    suspend fun ensureReferralProfile(userId: String) {
        dbQuery {
            val existing = ReferralProfiles.selectAll().where { ReferralProfiles.userId eq userId }.singleOrNull()
            if (existing == null) {
                val code = generateReferralCode(userId)
                ReferralProfiles.insert {
                    it[ReferralProfiles.userId] = userId
                    it[referralCode] = code
                    it[referralLink] = "https://yourapp.com/ref/$code"
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }

            val settings = ReferralProgramSettings.selectAll().where { ReferralProgramSettings.id eq SETTINGS_ID }.singleOrNull()
            if (settings == null) {
                ReferralProgramSettings.insert {
                    it[id] = SETTINGS_ID
                    it[rewardAmountCredits] = 50
                    it[percentageCommission] = 10
                    it[minimumActivityCredits] = 20
                    it[dailyReferralLimit] = 25
                    it[fraudBlockSameDevice] = true
                    it[fraudBlockSameIp] = true
                    it[fraudBlockEmulator] = true
                    it[fraudBlockMultipleAccounts] = true
                    it[active] = true
                    it[updatedAt] = Instant.now()
                }
            }
        }
    }

    suspend fun attachReferral(
        invitedUserId: String,
        referralCode: String?,
        deviceHash: String?,
        ipHash: String?,
        emulatorFlag: Boolean
    ) {
        if (referralCode.isNullOrBlank()) return

        dbQuery {
            val normalizedCode = referralCode.trim().uppercase()
            val inviterProfile = ReferralProfiles.selectAll().where { ReferralProfiles.referralCode eq normalizedCode }.singleOrNull()
                ?: throw ApiFailure(HttpStatusCode.BadRequest, "INVALID_REFERRAL_CODE", "Referral code not found")

            val inviterUserId = inviterProfile[ReferralProfiles.userId]
            if (inviterUserId == invitedUserId) {
                throw ApiFailure(HttpStatusCode.BadRequest, "SELF_REFERRAL_BLOCKED", "You cannot refer yourself")
            }

            val alreadyLinked = ReferralRelationships.selectAll().where { ReferralRelationships.invitedUserId eq invitedUserId }.singleOrNull()
            if (alreadyLinked != null) return@dbQuery

            val settings = getSettingsInline()
            val fraudReasons = mutableListOf<String>()

            if (settings.fraudBlockSameDevice && !deviceHash.isNullOrBlank()) {
                val inviterDevices = UserDevices.selectAll().where { UserDevices.userId eq inviterUserId }.map { it[UserDevices.deviceHash] }.toSet()
                if (inviterDevices.contains(deviceHash)) fraudReasons += "same_device"
            }

            if (settings.fraudBlockSameIp && !ipHash.isNullOrBlank()) {
                val inviterIps = AdWatchSessions.selectAll().where { AdWatchSessions.userId eq inviterUserId }
                    .orderBy(AdWatchSessions.startedAt, SortOrder.DESC)
                    .limit(10)
                    .map { it[AdWatchSessions.ipHash] }
                    .toSet()
                if (inviterIps.contains(ipHash)) fraudReasons += "same_ip"
            }

            if (settings.fraudBlockEmulator && emulatorFlag) {
                fraudReasons += "emulator_detected"
            }

            if (settings.fraudBlockMultipleAccounts && !deviceHash.isNullOrBlank()) {
                val duplicateUsers = UserDevices.selectAll().where { UserDevices.deviceHash eq deviceHash }
                    .map { it[UserDevices.userId] }
                    .distinct()
                if (duplicateUsers.any { it == inviterUserId }) {
                    fraudReasons += "multi_account_device"
                }
            }

            val status = if (fraudReasons.isEmpty()) "pending" else "rejected"
            ReferralRelationships.insert {
                it[id] = "ref_${UUID.randomUUID()}"
                it[ReferralRelationships.inviterUserId] = inviterUserId
                it[ReferralRelationships.invitedUserId] = invitedUserId
                it[ReferralRelationships.referralCode] = normalizedCode
                it[referralSource] = "manual"
                it[ReferralRelationships.status] = status
                it[invitedDeviceHash] = deviceHash
                it[invitedIpHash] = ipHash
                it[ReferralRelationships.fraudReasons] = fraudReasons.takeIf { reasons -> reasons.isNotEmpty() }?.joinToString(",")
                it[invitedAt] = Instant.now()
                it[approvedAt] = null
                it[rejectedAt] = if (status == "rejected") Instant.now() else null
                it[lastCommissionAt] = null
            }

            createNotificationInline(
                userId = inviterUserId,
                type = "referral_joined",
                title = "New referral joined",
                message = "Someone joined using your referral code.",
                metadata = buildJsonObject {
                    put("invitedUserId", invitedUserId)
                    put("status", status)
                }
            )
        }
    }

    suspend fun getInviteSummary(userId: String): ReferralInviteSummary = dbQuery {
        ensureReferralProfileInline(userId)
        val profile = ReferralProfiles.selectAll().where { ReferralProfiles.userId eq userId }.single()
        val relationships = ReferralRelationships.selectAll().where { ReferralRelationships.inviterUserId eq userId }.toList()
        val commissionLogs = ReferralCommissionLogs.selectAll().where { ReferralCommissionLogs.inviterUserId eq userId }.toList()
        val totalEarnings = commissionLogs.sumOf { it[ReferralCommissionLogs.creditsAwarded] }
        ReferralInviteSummary(
            referralCode = profile[ReferralProfiles.referralCode],
            referralLink = profile[ReferralProfiles.referralLink],
            currentReferralEarnings = totalEarnings,
            invitedUsersCount = relationships.size,
            activeReferralsCount = relationships.count { it[ReferralRelationships.status] == "approved" },
            pendingReferralsCount = relationships.count { it[ReferralRelationships.status] == "pending" },
            approvedReferralsCount = relationships.count { it[ReferralRelationships.status] == "approved" },
            rejectedReferralsCount = relationships.count { it[ReferralRelationships.status] == "rejected" },
            totalEarnings = totalEarnings
        )
    }

    suspend fun getReferralRelationships(userId: String): List<ReferralRelationshipSummary> = dbQuery {
        ReferralRelationships.selectAll()
            .where { ReferralRelationships.inviterUserId eq userId }
            .orderBy(ReferralRelationships.invitedAt, SortOrder.DESC)
            .map { row ->
                val invitedUserId = row[ReferralRelationships.invitedUserId]
                val invitedUser = Users.selectAll().where { Users.id eq invitedUserId }.singleOrNull()
                val totalCommission = ReferralCommissionLogs.selectAll().where {
                    ReferralCommissionLogs.relationshipId eq row[ReferralRelationships.id]
                }.sumOf { it[ReferralCommissionLogs.creditsAwarded] }
                ReferralRelationshipSummary(
                    relationshipId = row[ReferralRelationships.id],
                    invitedUserId = invitedUserId,
                    invitedEmail = invitedUser?.get(Users.email),
                    status = row[ReferralRelationships.status],
                    source = row[ReferralRelationships.referralSource],
                    fraudReasons = row[ReferralRelationships.fraudReasons],
                    invitedAt = row[ReferralRelationships.invitedAt].toString(),
                    approvedAt = row[ReferralRelationships.approvedAt]?.toString(),
                    rejectedAt = row[ReferralRelationships.rejectedAt]?.toString(),
                    totalCommissionEarned = totalCommission
                )
            }
    }

    suspend fun getProgramSettings(): ReferralProgramSettingsData = dbQuery {
        val row = getSettingsInline()
        ReferralProgramSettingsData(
            rewardAmountCredits = row.rewardAmountCredits,
            percentageCommission = row.percentageCommission,
            minimumActivityCredits = row.minimumActivityCredits,
            dailyReferralLimit = row.dailyReferralLimit,
            fraudBlockSameDevice = row.fraudBlockSameDevice,
            fraudBlockSameIp = row.fraudBlockSameIp,
            fraudBlockEmulator = row.fraudBlockEmulator,
            fraudBlockMultipleAccounts = row.fraudBlockMultipleAccounts,
            active = row.active
        )
    }

    suspend fun updateProgramSettings(payload: ReferralProgramSettingsData) {
        dbQuery {
            val existing = ReferralProgramSettings.selectAll().where { ReferralProgramSettings.id eq SETTINGS_ID }.singleOrNull()
            if (existing == null) {
                ReferralProgramSettings.insert {
                    it[id] = SETTINGS_ID
                    it[rewardAmountCredits] = payload.rewardAmountCredits
                    it[percentageCommission] = payload.percentageCommission
                    it[minimumActivityCredits] = payload.minimumActivityCredits
                    it[dailyReferralLimit] = payload.dailyReferralLimit
                    it[fraudBlockSameDevice] = payload.fraudBlockSameDevice
                    it[fraudBlockSameIp] = payload.fraudBlockSameIp
                    it[fraudBlockEmulator] = payload.fraudBlockEmulator
                    it[fraudBlockMultipleAccounts] = payload.fraudBlockMultipleAccounts
                    it[active] = payload.active
                    it[updatedAt] = Instant.now()
                }
            } else {
                ReferralProgramSettings.update({ ReferralProgramSettings.id eq SETTINGS_ID }) {
                    it[rewardAmountCredits] = payload.rewardAmountCredits
                    it[percentageCommission] = payload.percentageCommission
                    it[minimumActivityCredits] = payload.minimumActivityCredits
                    it[dailyReferralLimit] = payload.dailyReferralLimit
                    it[fraudBlockSameDevice] = payload.fraudBlockSameDevice
                    it[fraudBlockSameIp] = payload.fraudBlockSameIp
                    it[fraudBlockEmulator] = payload.fraudBlockEmulator
                    it[fraudBlockMultipleAccounts] = payload.fraudBlockMultipleAccounts
                    it[active] = payload.active
                    it[updatedAt] = Instant.now()
                }
            }
        }
    }

    suspend fun onUserEarned(invitedUserId: String, sourceSessionId: String, earnedCredits: Int) {
        if (earnedCredits <= 0) return

        dbQuery {
            val relationship = ReferralRelationships.selectAll().where {
                ReferralRelationships.invitedUserId eq invitedUserId
            }.singleOrNull() ?: return@dbQuery

            if (relationship[ReferralRelationships.status] == "rejected") return@dbQuery

            val settings = getSettingsInline()
            if (!settings.active) return@dbQuery

            val inviterUserId = relationship[ReferralRelationships.inviterUserId]
            val dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
            val earnedToday = ReferralCommissionLogs.selectAll().where {
                ReferralCommissionLogs.inviterUserId eq inviterUserId
            }.count {
                it[ReferralCommissionLogs.createdAt].isAfter(dayStart) || it[ReferralCommissionLogs.createdAt] == dayStart
            }
            if (earnedToday >= settings.dailyReferralLimit) return@dbQuery

            val totalInviteeCredits = LedgerEntries.selectAll().where {
                (LedgerEntries.userId eq invitedUserId) and (LedgerEntries.type eq "EARN")
            }.sumOf { it[LedgerEntries.creditsDelta] }
            if (totalInviteeCredits < settings.minimumActivityCredits) return@dbQuery

            val alreadyRewarded = ReferralCommissionLogs.selectAll().where {
                (ReferralCommissionLogs.relationshipId eq relationship[ReferralRelationships.id]) and
                    (ReferralCommissionLogs.sourceSessionId eq sourceSessionId)
            }.singleOrNull()
            if (alreadyRewarded != null) return@dbQuery

            val baseReward = settings.rewardAmountCredits
            val percentageReward = (earnedCredits * settings.percentageCommission) / 100
            val commission = baseReward + percentageReward
            if (commission <= 0) return@dbQuery

            val wallet = Wallets.selectAll().where { Wallets.userId eq inviterUserId }.singleOrNull()
                ?: run {
                    Wallets.insert {
                        it[userId] = inviterUserId
                        it[availableCredits] = 0
                        it[pendingCredits] = 0
                        it[reservedCredits] = 0
                        it[lifetimeEarnedCredits] = 0
                        it[updatedAt] = Instant.now()
                    }
                    Wallets.selectAll().where { Wallets.userId eq inviterUserId }.single()
                }

            Wallets.update({ Wallets.userId eq inviterUserId }) {
                it[availableCredits] = wallet[Wallets.availableCredits] + commission
                it[lifetimeEarnedCredits] = wallet[Wallets.lifetimeEarnedCredits] + commission
                it[updatedAt] = Instant.now()
            }

            val ledgerId = "led_${UUID.randomUUID()}"
            LedgerEntries.insert {
                it[id] = ledgerId
                it[userId] = inviterUserId
                it[type] = "REFERRAL_EARN"
                it[creditsDelta] = commission
                it[moneyEquivalentUsd] = java.math.BigDecimal(commission)
                    .divide(java.math.BigDecimal(100), 4, java.math.RoundingMode.HALF_UP)
                it[sourceType] = "referral_commission"
                it[sourceId] = relationship[ReferralRelationships.id]
                it[status] = "confirmed"
                it[createdAt] = Instant.now()
            }

            ReferralCommissionLogs.insert {
                it[id] = "rcl_${UUID.randomUUID()}"
                it[ReferralCommissionLogs.inviterUserId] = inviterUserId
                it[ReferralCommissionLogs.invitedUserId] = invitedUserId
                it[relationshipId] = relationship[ReferralRelationships.id]
                it[sourceLedgerId] = ledgerId
                it[ReferralCommissionLogs.sourceSessionId] = sourceSessionId
                it[commissionType] = if (relationship[ReferralRelationships.status] == "approved") "percentage" else "activation"
                it[creditsAwarded] = commission
                it[ReferralCommissionLogs.status] = "confirmed"
                it[metadataJson] = """{"earnedCredits":$earnedCredits,"baseReward":$baseReward,"percentageCommission":${settings.percentageCommission}}"""
                it[createdAt] = Instant.now()
            }

            if (relationship[ReferralRelationships.status] != "approved") {
                ReferralRelationships.update({ ReferralRelationships.id eq relationship[ReferralRelationships.id] }) {
                    it[status] = "approved"
                    it[approvedAt] = Instant.now()
                    it[lastCommissionAt] = Instant.now()
                }
            } else {
                ReferralRelationships.update({ ReferralRelationships.id eq relationship[ReferralRelationships.id] }) {
                    it[lastCommissionAt] = Instant.now()
                }
            }

            createNotificationInline(
                userId = inviterUserId,
                type = "referral_reward_added",
                title = "Referral reward added",
                message = "You earned $commission credits from one of your referrals.",
                metadata = buildJsonObject {
                    put("invitedUserId", invitedUserId)
                    put("commission", commission)
                }
            )

            AuditService.log(
                actorType = "system",
                actorId = inviterUserId,
                entityType = "referral_relationship",
                entityId = relationship[ReferralRelationships.id],
                action = "commission_awarded",
                metadata = buildJsonObject {
                    put("credits", commission)
                    put("sourceSessionId", sourceSessionId)
                }
            )
        }
    }

    suspend fun adminOverview(): AdminReferralOverview = dbQuery {
        val relationships = ReferralRelationships.selectAll().toList()
        val totalCommissionCredits = ReferralCommissionLogs.selectAll().sumOf { it[ReferralCommissionLogs.creditsAwarded] }
        AdminReferralOverview(
            totalReferrals = relationships.size,
            pendingReferrals = relationships.count { it[ReferralRelationships.status] == "pending" },
            approvedReferrals = relationships.count { it[ReferralRelationships.status] == "approved" },
            rejectedReferrals = relationships.count { it[ReferralRelationships.status] == "rejected" },
            totalCommissionCredits = totalCommissionCredits
        )
    }

    suspend fun adminRelationships(): List<Map<String, Any?>> = dbQuery {
        ReferralRelationships.selectAll()
            .orderBy(ReferralRelationships.invitedAt, SortOrder.DESC)
            .map { row ->
                val inviter = Users.selectAll().where { Users.id eq row[ReferralRelationships.inviterUserId] }.singleOrNull()
                val invited = Users.selectAll().where { Users.id eq row[ReferralRelationships.invitedUserId] }.singleOrNull()
                mapOf(
                    "id" to row[ReferralRelationships.id],
                    "inviterUserId" to row[ReferralRelationships.inviterUserId],
                    "inviterEmail" to inviter?.get(Users.email),
                    "invitedUserId" to row[ReferralRelationships.invitedUserId],
                    "invitedEmail" to invited?.get(Users.email),
                    "status" to row[ReferralRelationships.status],
                    "source" to row[ReferralRelationships.referralSource],
                    "fraudReasons" to row[ReferralRelationships.fraudReasons],
                    "invitedAt" to row[ReferralRelationships.invitedAt].toString(),
                    "approvedAt" to row[ReferralRelationships.approvedAt]?.toString(),
                    "rejectedAt" to row[ReferralRelationships.rejectedAt]?.toString()
                )
            }
    }

    suspend fun topInviters(): List<TopInviterSummary> = dbQuery {
        ReferralRelationships.selectAll()
            .where { ReferralRelationships.status eq "approved" }
            .groupBy { it[ReferralRelationships.inviterUserId] }
            .map { (userId, rows) ->
                val commissions = ReferralCommissionLogs.selectAll().where { ReferralCommissionLogs.inviterUserId eq userId }
                    .sumOf { it[ReferralCommissionLogs.creditsAwarded] }
                val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                TopInviterSummary(
                    userId = userId,
                    email = user?.get(Users.email),
                    approvedReferrals = rows.size,
                    totalCommissionCredits = commissions
                )
            }
            .sortedByDescending { it.totalCommissionCredits }
            .take(20)
    }

    suspend fun rejectRelationship(id: String, notes: String?) {
        dbQuery {
            ReferralRelationships.update({ ReferralRelationships.id eq id }) {
                it[status] = "rejected"
                it[rejectedAt] = Instant.now()
                it[fraudReasons] = notes ?: "Rejected by admin"
            }
        }
    }

    suspend fun approveRelationship(id: String) {
        dbQuery {
            ReferralRelationships.update({ ReferralRelationships.id eq id }) {
                it[status] = "approved"
                it[approvedAt] = Instant.now()
            }
        }
    }

    private fun generateReferralCode(userId: String): String {
        val suffix = userId.filter { it.isLetterOrDigit() }.takeLast(6).uppercase()
        return "USER$suffix"
    }

    private fun getSettingsInline(): ReferralProgramSettingsDataRow {
        val row = ReferralProgramSettings.selectAll().where { ReferralProgramSettings.id eq SETTINGS_ID }.singleOrNull()
        return if (row != null) {
            ReferralProgramSettingsDataRow(
                rewardAmountCredits = row[ReferralProgramSettings.rewardAmountCredits],
                percentageCommission = row[ReferralProgramSettings.percentageCommission],
                minimumActivityCredits = row[ReferralProgramSettings.minimumActivityCredits],
                dailyReferralLimit = row[ReferralProgramSettings.dailyReferralLimit],
                fraudBlockSameDevice = row[ReferralProgramSettings.fraudBlockSameDevice],
                fraudBlockSameIp = row[ReferralProgramSettings.fraudBlockSameIp],
                fraudBlockEmulator = row[ReferralProgramSettings.fraudBlockEmulator],
                fraudBlockMultipleAccounts = row[ReferralProgramSettings.fraudBlockMultipleAccounts],
                active = row[ReferralProgramSettings.active]
            )
        } else {
            ReferralProgramSettingsDataRow(50, 10, 20, 25, true, true, true, true, true)
        }
    }

    private fun ensureReferralProfileInline(userId: String) {
        val existing = ReferralProfiles.selectAll().where { ReferralProfiles.userId eq userId }.singleOrNull()
        if (existing == null) {
            val code = generateReferralCode(userId)
            ReferralProfiles.insert {
                it[ReferralProfiles.userId] = userId
                it[referralCode] = code
                it[referralLink] = "https://yourapp.com/ref/$code"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun createNotificationInline(
        userId: String,
        type: String,
        title: String,
        message: String,
        metadata: kotlinx.serialization.json.JsonObject? = null
    ) {
        UserNotifications.insert {
            it[id] = "noti_${UUID.randomUUID()}"
            it[UserNotifications.userId] = userId
            it[UserNotifications.type] = type
            it[UserNotifications.title] = title
            it[UserNotifications.message] = message
            it[status] = "unread"
            it[metadataJson] = metadata?.toString()
            it[createdAt] = Instant.now()
        }
    }

    private data class ReferralProgramSettingsDataRow(
        val rewardAmountCredits: Int,
        val percentageCommission: Int,
        val minimumActivityCredits: Int,
        val dailyReferralLimit: Int,
        val fraudBlockSameDevice: Boolean,
        val fraudBlockSameIp: Boolean,
        val fraudBlockEmulator: Boolean,
        val fraudBlockMultipleAccounts: Boolean,
        val active: Boolean
    )
}