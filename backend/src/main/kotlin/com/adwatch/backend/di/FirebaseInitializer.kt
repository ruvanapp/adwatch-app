package com.adwatch.backend.di

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.auth.oauth2.GoogleCredentials
import io.ktor.server.config.*
import java.io.FileInputStream

object FirebaseInitializer {
    
    fun initialize(config: ApplicationConfig) {
        try {
            val credentialsPath = config.property("firebase.credentialsPath").getString()
            val serviceAccount = FileInputStream(credentialsPath)
            
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build()
            
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
            }
        } catch (e: Exception) {
            println("Warning: Firebase initialization failed: ${e.message}")
            println("Firebase authentication will not work. Ensure firebase-credentials.json is configured.")
        }
    }
    
    fun getAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
