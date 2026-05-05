package com.adwatch.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adwatch.core.network.interceptor.SessionManager
import com.adwatch.core.storage.preferences.AppPreferences
import com.adwatch.feature.auth.AuthApiService
import com.adwatch.feature.auth.SignupRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val country: String = "",
    val isLoading: Boolean = false,
    val isSignedUp: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authApiService: AuthApiService,
    private val appPreferences: AppPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()
    
    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }
    
    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }
    
    fun onCountryChanged(country: String) {
        _uiState.value = _uiState.value.copy(country = country, error = null)
    }
    
    fun signup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val response = authApiService.signup(
                    SignupRequest(
                        email = _uiState.value.email,
                        password = _uiState.value.password,
                        country = _uiState.value.country
                    )
                )
                
                if (response.success) {
                    val userId = response.data?.userId
                    if (userId != null) {
                        appPreferences.setUserId(userId)
                        appPreferences.setUserEmail(_uiState.value.email)
                        appPreferences.setLoggedIn(true)
                        SessionManager.userId = userId
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
}
