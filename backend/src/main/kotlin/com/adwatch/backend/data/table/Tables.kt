package com.adwatch.backend.data.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object Users : Table("users") {
    val id = varchar("id", 50).uniqueIndex()
    val authProviderId = varchar("auth_provider_id", 255).uniqueIndex()
    val email = varchar("email", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val country = varchar("country", 10)
    val status = varchar("status", 50).default("active") // active, under_review, banned, suspended
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object UserDevices : Table("user_devices") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id)
    val deviceHash = varchar("device_hash", 255).index()
    val integrityVerdict = varchar("integrity_verdict", 50).nullable()
    val emulatorFlag = bool("emulator_flag").default(false)
    val rootFlag = bool("root_flag").default(false)
    val hookFlag = bool("hook_flag").default(false)
    val firstSeenAt = timestamp("first_seen_at").clientDefault { Instant.now() }
    val lastSeenAt = timestamp("last_seen_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("sessions") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id)
    val refreshTokenMeta = text("refresh_token_meta").nullable()
    val ipHash = varchar("ip_hash", 255)
    val userAgent = text("user_agent").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val expiresAt = timestamp("expires_at")
    
    override val primaryKey = PrimaryKey(id)
}

object AdWatchSessions : Table("ad_watch_sessions") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id)
    val adNetwork = varchar("ad_network", 50)
    val adUnitId = varchar("ad_unit_id", 255)
    val placement = varchar("placement", 100)
    val startedAt = timestamp("started_at").clientDefault { Instant.now() }
    val completedAt = timestamp("completed_at").nullable()
    val sdkRewardAmount = integer("sdk_reward_amount").nullable()
    val sdkRewardType = varchar("sdk_reward_type", 50).nullable()
    val claimStatus = varchar("claim_status", 50).default("pending") // pending, validated, rejected, rewarded
    val claimIdempotencyKey = varchar("claim_idempotency_key", 255).nullable().uniqueIndex()
    val deviceId = varchar("device_id", 50).nullable()
    val ipHash = varchar("ip_hash", 255)
    
    override val primaryKey = PrimaryKey(id)
}

object RewardRules : Table("reward_rules") {
    val id = varchar("id", 50)
    val countryGroup = varchar("country_group", 50) // US_UK, INDIA, TIER3, etc.
    val creditsPerCompletion = integer("credits_per_completion")
    val dailyCap = integer("daily_cap")
    val hourlyCap = integer("hourly_cap")
    val cooldownSeconds = integer("cooldown_seconds")
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object LedgerEntries : Table("ledger_entries") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id).index()
    val type = varchar("type", 50) // ad_reward, cashout_reserved, cashout_paid, cashout_reversed
    val creditsDelta = integer("credits_delta") // can be positive or negative
    val moneyEquivalentUsd = decimal("money_equivalent_usd", 10, 4).nullable()
    val sourceType = varchar("source_type", 50) // ad_session, cashout_request, manual_adjustment
    val sourceId = varchar("source_id", 255).nullable()
    val status = varchar("status", 50).default("confirmed") // confirmed, pending, reversed
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object Wallets : Table("wallets") {
    val userId = varchar("user_id", 50).references(Users.id)
    val availableCredits = integer("available_credits").default(0)
    val pendingCredits = integer("pending_credits").default(0)
    val reservedCredits = integer("reserved_credits").default(0)
    val lifetimeEarnedCredits = integer("lifetime_earned_credits").default(0)
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(userId)
}

object CashoutRequests : Table("cashout_requests") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id).index()
    val paypalEmail = varchar("paypal_email", 255)
    val requestedCredits = integer("requested_credits")
    val requestedAmountUsd = decimal("requested_amount_usd", 10, 2)
    val status = varchar("status", 50).default("pending") // pending, approved, rejected, paid, failed
    val reviewerId = varchar("reviewer_id", 50).nullable()
    val reviewNotes = text("review_notes").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val reviewedAt = timestamp("reviewed_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object PayoutTransactions : Table("payout_transactions") {
    val id = varchar("id", 50)
    val cashoutRequestId = varchar("cashout_request_id", 50).references(CashoutRequests.id)
    val provider = varchar("provider", 50) // paypal, manual
    val providerBatchId = varchar("provider_batch_id", 255).nullable()
    val providerItemId = varchar("provider_item_id", 255).nullable()
    val amount = decimal("amount", 10, 2)
    val currency = varchar("currency", 10).default("USD")
    val status = varchar("status", 50).default("pending") // pending, success, failed
    val rawResponseRef = text("raw_response_ref").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val completedAt = timestamp("completed_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object FraudEvents : Table("fraud_events") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id).nullable().index()
    val deviceId = varchar("device_id", 50).nullable().index()
    val eventType = varchar("event_type", 100) // duplicate_device, velocity_exceeded, suspicious_pattern, etc.
    val severity = varchar("severity", 20) // low, medium, high, critical
    val evidenceJson = text("evidence_json")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object AuditLogs : Table("audit_logs") {
    val id = varchar("id", 50)
    val actorType = varchar("actor_type", 50) // user, admin, system
    val actorId = varchar("actor_id", 50).nullable()
    val entityType = varchar("entity_type", 50) // user, cashout_request, wallet, etc.
    val entityId = varchar("entity_id", 50)
    val action = varchar("action", 100) // created, updated, approved, rejected, etc.
    val metadataJson = text("metadata_json").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    
    override val primaryKey = PrimaryKey(id)
}

object FeatureFlags : Table("feature_flags") {
    val flagName = varchar("flag_name", 100)
    val enabled = bool("enabled").default(true)
    val countryScope = varchar("country_scope", 255).nullable() // null or "*" means global, else CSV country list/groups
    val updatedBy = varchar("updated_by", 50).nullable()
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(flagName)
}

object ReferralProfiles : Table("referral_profiles") {
    val userId = varchar("user_id", 50).references(Users.id)
    val referralCode = varchar("referral_code", 32).uniqueIndex()
    val referralLink = varchar("referral_link", 512)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(userId)
}

object ReferralRelationships : Table("referral_relationships") {
    val id = varchar("id", 50)
    val inviterUserId = varchar("inviter_user_id", 50).references(Users.id).index()
    val invitedUserId = varchar("invited_user_id", 50).references(Users.id).uniqueIndex()
    val referralCode = varchar("referral_code", 32).index()
    val referralSource = varchar("referral_source", 20).default("manual")
    val status = varchar("status", 20).default("pending")
    val invitedDeviceHash = varchar("invited_device_hash", 255).nullable().index()
    val invitedIpHash = varchar("invited_ip_hash", 255).nullable().index()
    val fraudReasons = text("fraud_reasons").nullable()
    val invitedAt = timestamp("invited_at").clientDefault { Instant.now() }
    val approvedAt = timestamp("approved_at").nullable()
    val rejectedAt = timestamp("rejected_at").nullable()
    val lastCommissionAt = timestamp("last_commission_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ReferralProgramSettings : Table("referral_program_settings") {
    val id = varchar("id", 20)
    val rewardAmountCredits = integer("reward_amount_credits").default(50)
    val percentageCommission = integer("percentage_commission").default(10)
    val minimumActivityCredits = integer("minimum_activity_credits").default(20)
    val dailyReferralLimit = integer("daily_referral_limit").default(25)
    val fraudBlockSameDevice = bool("fraud_block_same_device").default(true)
    val fraudBlockSameIp = bool("fraud_block_same_ip").default(true)
    val fraudBlockEmulator = bool("fraud_block_emulator").default(true)
    val fraudBlockMultipleAccounts = bool("fraud_block_multiple_accounts").default(true)
    val active = bool("active").default(true)
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object ReferralCommissionLogs : Table("referral_commission_logs") {
    val id = varchar("id", 50)
    val inviterUserId = varchar("inviter_user_id", 50).references(Users.id).index()
    val invitedUserId = varchar("invited_user_id", 50).references(Users.id).index()
    val relationshipId = varchar("relationship_id", 50).references(ReferralRelationships.id).index()
    val sourceLedgerId = varchar("source_ledger_id", 50).nullable().index()
    val sourceSessionId = varchar("source_session_id", 50).nullable()
    val commissionType = varchar("commission_type", 20)
    val creditsAwarded = integer("credits_awarded")
    val status = varchar("status", 20).default("confirmed")
    val metadataJson = text("metadata_json").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object UserNotifications : Table("user_notifications") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).references(Users.id).index()
    val type = varchar("type", 50)
    val title = varchar("title", 120)
    val message = text("message")
    val status = varchar("status", 20).default("unread")
    val metadataJson = text("metadata_json").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}
