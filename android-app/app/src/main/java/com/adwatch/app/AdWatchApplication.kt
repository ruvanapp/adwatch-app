package com.adwatch.app

import android.app.Application
import com.adwatch.core.network.interceptor.SessionManager
import com.adwatch.core.storage.preferences.AppPreferences
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class AdWatchApplication : Application() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize AppLovin MAX
        AppLovinSdk.getInstance(this).apply {
            mediationProvider = AppLovinMediationProvider.MAX
            initializeSdk { /* SDK ready */ }
        }

        // Restore auth session from storage
        runBlocking {
            val userId = appPreferences.userId.first()
            val authToken = appPreferences.authToken.first()
            if (userId != null) SessionManager.userId = userId
            if (authToken != null) SessionManager.authToken = authToken
        }
    }
}
