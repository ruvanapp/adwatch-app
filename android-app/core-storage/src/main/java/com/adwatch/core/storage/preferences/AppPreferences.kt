package com.adwatch.core.storage.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "adwatch_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dataStore = context.dataStore
    
    companion object {
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")
    }
    
    val userId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[USER_ID_KEY]
    }
    
    val userEmail: Flow<String?> = dataStore.data.map { prefs ->
        prefs[USER_EMAIL_KEY]
    }
    
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[IS_LOGGED_IN_KEY] ?: false
    }
    
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETE_KEY] ?: false
    }
    
    suspend fun setUserId(userId: String?) {
        dataStore.edit { prefs ->
            if (userId != null) {
                prefs[USER_ID_KEY] = userId
            } else {
                prefs.remove(USER_ID_KEY)
            }
        }
    }
    
    suspend fun setUserEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email != null) {
                prefs[USER_EMAIL_KEY] = email
            } else {
                prefs.remove(USER_EMAIL_KEY)
            }
        }
    }
    
    suspend fun setLoggedIn(isLoggedIn: Boolean) {
        dataStore.edit { prefs ->
            prefs[IS_LOGGED_IN_KEY] = isLoggedIn
        }
    }
    
    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETE_KEY] = complete
        }
    }
    
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
