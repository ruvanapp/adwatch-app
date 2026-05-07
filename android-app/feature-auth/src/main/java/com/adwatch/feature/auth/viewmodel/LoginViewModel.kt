package com.adwatch.feature.auth.viewmodel

import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adwatch.core.network.interceptor.SessionManager
import com.adwatch.core.storage.preferences.AppPreferences
import com.adwatch.feature.auth.AuthApiService
import com.adwatch.feature.auth.GoogleAuthManager
import com.adwatch.feature.auth.GoogleLoginRequest
import com.adwatch.feature.auth.LoginRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApiService: AuthApiService,
    private val appPreferences: AppPreferences,
    private val firebaseAuth: FirebaseAuth,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }
    
    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }
    
    fun login() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Sign in with Firebase email/password
                val authResult = firebaseAuth
                    .signInWithEmailAndPassword(_uiState.value.email, _uiState.value.password)
                    .await()
                val firebaseUser = authResult.user
                    ?: throw IllegalStateException("Firebase login failed")
                val firebaseIdToken = firebaseUser.getIdToken(true).await().token
                    ?: throw IllegalStateException("Unable to get Firebase token")
                val country = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"

                // Register/fetch app user via the same /auth/google endpoint
                val response = authApiService.loginWithGoogle(
                    authorization = "Bearer $firebaseIdToken",
                    request = GoogleLoginRequest(country = country)
                )

                if (response.success) {
                    val userId = response.data?.userId ?: throw IllegalStateException("User ID missing")
                    appPreferences.setUserId(userId)
                    appPreferences.setUserEmail(firebaseUser.email)
                    appPreferences.setAuthToken(firebaseIdToken)
                    appPreferences.setLoggedIn(true)
                    SessionManager.userId = userId
                    SessionManager.authToken = firebaseIdToken
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Login failed"
                    )
                }
            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Wrong email or password"
                )
            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No account found. Please sign up first."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Login failed"
                )
            }
        }
    }

    fun getGoogleSignInIntent() = googleAuthManager.getSignInIntent()

    fun handleGoogleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val idToken = googleAuthManager.extractIdToken(data)
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user ?: throw IllegalStateException("Google user not found")
                val firebaseIdToken = firebaseUser.getIdToken(true).await().token
                    ?: throw IllegalStateException("Unable to get Firebase token")
                val country = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"

                val response = authApiService.loginWithGoogle(
                    authorization = "Bearer $firebaseIdToken",
                    request = GoogleLoginRequest(country = country)
                )

                if (response.success) {
                    val userId = response.data?.userId ?: throw IllegalStateException("User ID missing")
                    appPreferences.setUserId(userId)
                    appPreferences.setUserEmail(firebaseUser.email)
                    appPreferences.setAuthToken(firebaseIdToken)
                    appPreferences.setLoggedIn(true)
                    SessionManager.userId = userId
                    SessionManager.authToken = firebaseIdToken
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Google login failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (e.message?.contains("cancel", ignoreCase = true) == true)
                        "Sign-in cancelled" else (e.message ?: "Google sign-in failed")
                )
            }
        }
    }
}
