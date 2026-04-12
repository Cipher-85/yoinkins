package com.apkpackager.ui.auth

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import javax.inject.Inject

sealed class LoginState {
    object Idle : LoginState()
    data class Loading(val message: String) : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LoginState>(
        if (authRepository.isLoggedIn()) LoginState.Success else LoginState.Idle
    )
    val state: StateFlow<LoginState> = _state

    private var authService: AuthorizationService? = null

    fun startLogin(launchIntent: (Intent) -> Unit) {
        authService?.dispose()
        val service = AuthorizationService(getApplication())
        authService = service
        val authIntent = service.getAuthorizationRequestIntent(authRepository.buildAuthRequest())
        launchIntent(authIntent)
        _state.value = LoginState.Loading("Waiting for GitHub authorization...")
    }

    fun handleAuthResponse(intent: Intent) {
        authService?.dispose()
        authService = null
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        when {
            exception != null -> {
                _state.value = LoginState.Error(exception.errorDescription ?: "Authorization failed")
            }
            response != null -> {
                _state.value = LoginState.Loading("Exchanging token...")
                viewModelScope.launch {
                    try {
                        authRepository.exchangeCode(getApplication(), response)
                        _state.value = LoginState.Success
                    } catch (e: Exception) {
                        _state.value = LoginState.Error(e.message ?: "Token exchange failed")
                    }
                }
            }
            else -> {
                _state.value = LoginState.Idle
            }
        }
    }

    override fun onCleared() {
        authService?.dispose()
        super.onCleared()
    }
}
