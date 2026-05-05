package com.adwatch.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import com.adwatch.backend.data.table.*

object DatabaseFactory {
    
    fun init(config: ApplicationConfig) {
        val url = config.property("database.url").getString()
        val driver = config.property("database.driver").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        val maxPoolSize = config.property("database.maxPoolSize").getString().toInt()
        
        Database.connect(createHikariDataSource(url, driver, user, password, maxPoolSize))
        
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
            validate()
        }
        return HikariDataSource(config)
    }
    
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
