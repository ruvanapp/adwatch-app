package com.adwatch.backend

import com.adwatch.backend.config.DatabaseFactory
import com.adwatch.backend.di.FirebaseInitializer
import com.adwatch.backend.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // Initialize Firebase
    FirebaseInitializer.initialize(environment.config)

    // Initialize database
    DatabaseFactory.init(environment.config)

    // Configure plugins
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureSecurity()
    configureRouting()
}
