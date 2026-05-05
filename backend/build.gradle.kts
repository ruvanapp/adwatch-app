plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    id("io.ktor.plugin") version "2.3.7"
    id("org.jetbrains.kotlin.kapt") version "1.9.20"
}

group = "com.adwatch"
version = "1.0.0"

application {
    mainClass.set("com.adwatch.backend.ApplicationKt")
}

repositories {
    mavenCentral()
}

configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.guava:listenablefuture") {
        select("com.google.guava:guava:0")
    }
}

dependencies {
    val ktorVersion = "2.3.7"
    val exposedVersion = "0.46.0"
    val hikariVersion = "5.1.0"
    val postgresVersion = "42.7.1"
    val redisVersion = "5.0.2"
    val koinVersion = "3.5.3"
    
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    
    // Ktor Client (for external APIs like PayPal)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    
    // Database - Exposed
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    
    // Database - PostgreSQL & Connection Pool
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    
    // Redis
    implementation("io.lettuce:lettuce-core:6.3.1.RELEASE")
    
    // Dependency Injection - Koin
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")
    
    // Config
    implementation("com.typesafe:config:1.4.3")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.20")
    testImplementation("io.mockk:mockk:1.13.9")
}

kotlin {
    jvmToolchain(17)
}
