package com.adwatch.backend.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

@Serializable
data class User(
    val id: String,
    val authProviderId: String,
    val email: String?,
    val phone: String?,
    val country: String,
    val status: UserStatus,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)

enum class UserStatus {
    ACTIVE,
    UNDER_REVIEW,
    BANNED,
    SUSPENDED
}

@Serializable
data class Wallet(
    val userId: String,
    val availableCredits: Int,
    val pendingCredits: Int,
    val reservedCredits: Int,
    val lifetimeEarnedCredits: Int,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)

@Serializable
data class LedgerEntry(
    val id: String,
    val userId: String,
    val type: LedgerEntryType,
    val creditsDelta: Int,
    val moneyEquivalentUsd: String?,
    val sourceType: String,
    val sourceId: String?,
    val status: LedgerEntryStatus,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant
)

enum class LedgerEntryType {
    AD_REWARD,
    CASHOUT_RESERVED,
    CASHOUT_PAID,
    CASHOUT_REVERSED,
    MANUAL_ADJUSTMENT
}

enum class LedgerEntryStatus {
    CONFIRMED,
    PENDING,
    REVERSED
}

@Serializable
data class AdWatchSession(
    val id: String,
    val userId: String,
    val adNetwork: String,
    val adUnitId: String,
    val placement: String,
    @Serializable(with = InstantSerializer::class)
    val startedAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val completedAt: Instant?,
    val sdkRewardAmount: Int?,
    val sdkRewardType: String?,
    val claimStatus: ClaimStatus,
    val deviceId: String?,
    val ipHash: String
)

enum class ClaimStatus {
    PENDING,
    VALIDATED,
    REJECTED,
    REWARDED
}

@Serializable
data class CashoutRequest(
    val id: String,
    val userId: String,
    val paypalEmail: String,
    val requestedCredits: Int,
    val requestedAmountUsd: String,
    val status: CashoutStatus,
    val reviewerId: String?,
    val reviewNotes: String?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val reviewedAt: Instant?
)

enum class CashoutStatus {
    PENDING,
    HOLD,
    APPROVED,
    REJECTED,
    PAID,
    FAILED
}

@Serializable
data class RewardRule(
    val id: String,
    val countryGroup: String,
    val creditsPerCompletion: Int,
    val dailyCap: Int,
    val hourlyCap: Int,
    val cooldownSeconds: Int,
    val active: Boolean
)

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
