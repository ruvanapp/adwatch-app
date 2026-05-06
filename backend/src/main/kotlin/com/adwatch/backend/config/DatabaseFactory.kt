package com.adwatch.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import java.net.URI
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import com.adwatch.backend.data.table.*

object DatabaseFactory {
    
    fun init(config: ApplicationConfig) {
        val rawUrl = System.getenv("DATABASE_URL")
            ?: config.propertyOrNull("database.url")?.getString()
            ?: error("DATABASE_URL is missing")
        val driver = config.property("database.driver").getString()
        val maxPoolSize = config.property("database.maxPoolSize").getString().toInt()
        val databaseConfig = normalizeDatabaseConfig(rawUrl, config)
        
        Database.connect(
            createHikariDataSource(
                databaseConfig.url,
                driver,
                databaseConfig.user,
                databaseConfig.password,
                maxPoolSize
            )
        )
        
        // Create tables
        transaction {
            SchemaUtils.create(
                Users,
                UserDevices,
                Sessions,
                AdWatchSessions,
                RewardRules,
                LedgerEntries,
                Wallets,
                CashoutRequests,
                PayoutTransactions,
                FraudEvents,
                AuditLogs,
                FeatureFlags
            )
        }
    }
    
    private data class DatabaseConnectionConfig(
        val url: String,
        val user: String,
        val password: String
    )

    private fun normalizeDatabaseConfig(rawUrl: String, config: ApplicationConfig): DatabaseConnectionConfig {
        var url = rawUrl.trim()
        var user = config.propertyOrNull("database.user")?.getString().orEmpty()
        var password = config.propertyOrNull("database.password")?.getString().orEmpty()

        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            val normalizedUri = URI(url.replaceFirst("postgres://", "postgresql://"))
            val userInfo = normalizedUri.userInfo
            if (!userInfo.isNullOrBlank()) {
                val parts = userInfo.split(":", limit = 2)
                user = parts.getOrElse(0) { user }
                password = parts.getOrElse(1) { password }
            }
            val query = normalizedUri.rawQuery?.let { "?$it" }.orEmpty()
            url = "jdbc:postgresql://${normalizedUri.host}:${normalizedUri.port}${normalizedUri.path}$query"
        }

        require(url.startsWith("jdbc:postgresql://")) { "Invalid PostgreSQL JDBC URL" }
        require(user.isNotBlank()) { "DATABASE_USER is missing and DATABASE_URL has no user" }

        return DatabaseConnectionConfig(url, user, password)
    }

    private fun createHikariDataSource(
        url: String,
        driver: String,
        user: String,
        password: String,
        maxPoolSize: Int
    ): HikariDataSource {
        val config = HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        return HikariDataSource(config)
    }
    
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
