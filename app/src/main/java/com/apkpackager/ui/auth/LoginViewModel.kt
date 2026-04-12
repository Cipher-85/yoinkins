package com.apkpackager.ui.auth

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResult
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

    fun buildAuthIntent(): Intent {
        val service = AuthorizationService(getApplication())
        val intent = service.getAuthorizationRequestIntent(authRepository.buildAuthRequest())
        service.dispose()
        return intent
    }

    fun startLogin() {
        _state.value = LoginState.Loading("Waiting for GitHub authorization...")
    }

    fun handleAuthResult(result: ActivityResult) {
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            _state.value = LoginState.Idle
            return
        }

        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)

        when {
            error != null -> {
                _state.value = LoginState.Error(
                    error.errorDescription ?: error.error ?: "Authorization failed"
                )
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
            else -> _state.value = LoginState.Idle
        }
    }
}
