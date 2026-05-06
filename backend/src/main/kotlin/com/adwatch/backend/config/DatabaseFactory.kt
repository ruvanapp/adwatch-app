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
        val rawUrls = listOfNotNull(
            System.getenv("DATABASE_URL"),
            System.getenv("DATABASE_PRIVATE_URL"),
            System.getenv("DATABASE_PUBLIC_URL"),
            System.getenv("POSTGRES_URL"),
            System.getenv("POSTGRESQL_URL"),
            System.getenv("POSTGRES_PRIVATE_URL"),
            System.getenv("POSTGRES_PUBLIC_URL")
        )
        val driver = config.property("database.driver").getString()
        val maxPoolSize = config.property("database.maxPoolSize").getString().toInt()
        val databaseConfig = rawUrls
            .asSequence()
            .mapNotNull { normalizeDatabaseConfig(it, config) }
            .firstOrNull()
            ?: databaseConfigFromParts(config)
            ?: error("No valid PostgreSQL database configuration found")

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

    private fun normalizeDatabaseConfig(rawUrl: String, config: ApplicationConfig): DatabaseConnectionConfig? {
        var url = rawUrl.trim().removeSurrounding("\"")
        var user = firstNonBlank(
            System.getenv("PGUSER"),
            System.getenv("POSTGRES_USER"),
            System.getenv("DATABASE_USER"),
            config.propertyOrNull("database.user")?.getString()
        ).orEmpty()
        var password = firstNonBlank(
            System.getenv("PGPASSWORD"),
            System.getenv("POSTGRES_PASSWORD"),
            System.getenv("DATABASE_PASSWORD"),
            config.propertyOrNull("database.password")?.getString()
        ).orEmpty()

        if (url.isBlank() || url.startsWith("\${{") || url.startsWith("$")) {
            return null
        }

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

        if (url.startsWith("jdbc:postgres://")) {
            url = url.replaceFirst("jdbc:postgres://", "jdbc:postgresql://")
        }

        if (!url.startsWith("jdbc:postgresql://") || user.isBlank()) {
            return null
        }

        return DatabaseConnectionConfig(url, user, password)
    }

    private fun databaseConfigFromParts(config: ApplicationConfig): DatabaseConnectionConfig? {
        val host = firstNonBlank(
            System.getenv("PGHOST"),
            System.getenv("POSTGRES_HOST"),
            System.getenv("DATABASE_HOST")
        ) ?: return null
        val port = firstNonBlank(
            System.getenv("PGPORT"),
            System.getenv("POSTGRES_PORT"),
            System.getenv("DATABASE_PORT")
        ) ?: "5432"
        val database = firstNonBlank(
            System.getenv("PGDATABASE"),
            System.getenv("POSTGRES_DB"),
            System.getenv("DATABASE_NAME")
        ) ?: "railway"
        val user = firstNonBlank(
            System.getenv("PGUSER"),
            System.getenv("POSTGRES_USER"),
            System.getenv("DATABASE_USER"),
            config.propertyOrNull("database.user")?.getString()
        ) ?: return null
        val password = firstNonBlank(
            System.getenv("PGPASSWORD"),
            System.getenv("POSTGRES_PASSWORD"),
            System.getenv("DATABASE_PASSWORD"),
            config.propertyOrNull("database.password")?.getString()
        ).orEmpty()

        return DatabaseConnectionConfig("jdbc:postgresql://$host:$port/$database", user, password)
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

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
