package com.adwatch.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.adwatch.app.navigation.AdWatchNavHost
import com.adwatch.core.storage.preferences.AppPreferences
import com.adwatch.core.ui.theme.AdWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleReferralIntent(intent)
        enableEdgeToEdge()
        
        setContent {
            AdWatchTheme {
                val navController = rememberNavController()
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AdWatchNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReferralIntent(intent)
    }

    private fun handleReferralIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.host != "yourapp.com") return
        val segments = data.pathSegments
        if (segments.size >= 2 && segments[0] == "ref") {
            val referralCode = segments[1]
            CoroutineScope(Dispatchers.IO).launch {
                appPreferences.setPendingReferralCode(referralCode)
            }
        }
    }
}
