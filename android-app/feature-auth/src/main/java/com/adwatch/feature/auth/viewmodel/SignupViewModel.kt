package com.adwatch.feature.auth.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adwatch.core.network.interceptor.SessionManager
import com.adwatch.core.storage.preferences.AppPreferences
import com.adwatch.feature.auth.AuthApiService
import com.adwatch.feature.auth.GoogleAuthManager
import com.adwatch.feature.auth.GoogleLoginRequest
import com.adwatch.feature.auth.SignupRequest
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject

data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val country: String = "",
    val referralCode: String = "",
    val isLoading: Boolean = false,
    val isSignedUp: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authApiService: AuthApiService,
    private val appPreferences: AppPreferences,
    private val firebaseAuth: FirebaseAuth,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val pendingReferralCode = appPreferences.pendingReferralCode.first()
            if (!pendingReferralCode.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(referralCode = pendingReferralCode)
            }
        }
    }
    
    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }
    
    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }
    
    fun onCountryChanged(country: String) {
        _uiState.value = _uiState.value.copy(country = country, error = null)
    }

    fun onReferralCodeChanged(referralCode: String) {
        _uiState.value = _uiState.value.copy(referralCode = referralCode, error = null)
    }
    
    fun signup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val response = authApiService.signup(
                    SignupRequest(
                        email = _uiState.value.email,
                        password = _uiState.value.password,
                        country = _uiState.value.country,
                        referralCode = _uiState.value.referralCode.takeIf { it.isNotBlank() }
                    )
                )
                
                if (response.success) {
                    val userId = response.data?.userId
                    if (userId != null) {
                        appPreferences.setUserId(userId)
                        appPreferences.setUserEmail(_uiState.value.email)
                        appPreferences.setReferralCode(response.data?.referralCode)
                        appPreferences.setPendingReferralCode(null)
                        appPreferences.setLoggedIn(true)
                        SessionManager.userId = userId
                    }
                    // Create Firebase email/password account so the user can log back in later
                    try {
                        firebaseAuth.createUserWithEmailAndPassword(
                            _uiState.value.email,
                            _uiState.value.password
                        ).await()
                    } catch (_: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        // Account already exists in Firebase — that's fine
                    } catch (_: Exception) {
                        // Non-critical: backend account already created, ignore Firebase failure
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedUp = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Signup failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Signup failed"
                )
            }
        }
    }

    fun signupWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val idToken = googleAuthManager.getIdToken(activity)
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user ?: throw IllegalStateException("Google user not found")
                val firebaseIdToken = firebaseUser.getIdToken(true).await().token
                    ?: throw IllegalStateException("Unable to get Firebase token")
                val country = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"

                val response = authApiService.loginWithGoogle(
                    authorization = "Bearer $firebaseIdToken",
                    request = GoogleLoginRequest(
                        country = country,
                        referralCode = _uiState.value.referralCode.takeIf { it.isNotBlank() }
                    )
                )

                if (response.success) {
                    val userId = response.data?.userId ?: throw IllegalStateException("User ID missing")
                    appPreferences.setUserId(userId)
                    appPreferences.setUserEmail(firebaseUser.email)
                    appPreferences.setAuthToken(firebaseIdToken)
                    appPreferences.setReferralCode(response.data?.referralCode)
                    appPreferences.setPendingReferralCode(null)
                    appPreferences.setLoggedIn(true)
                    SessionManager.userId = userId
                    SessionManager.authToken = firebaseIdToken
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedUp = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Google sign up failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Google sign up failed"
                )
            }
        }
    }
}
