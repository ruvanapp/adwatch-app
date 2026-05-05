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
