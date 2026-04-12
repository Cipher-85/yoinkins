package com.apkpackager.ui.auth

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.auth.AuthRepository
import com.apkpackager.data.auth.AuthResultBus
import com.apkpackager.ui.MainActivity
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
    private val authRepository: AuthRepository,
    private val authResultBus: AuthResultBus
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LoginState>(
        if (authRepository.isLoggedIn()) LoginState.Success else LoginState.Idle
    )
    val state: StateFlow<LoginState> = _state

    private var authService: AuthorizationService? = null

    init {
        viewModelScope.launch {
            authResultBus.results.collect { intent -> handleAuthResponse(intent) }
        }
    }

    fun startLogin(context: Context) {
        authService?.dispose()
        val service = AuthorizationService(context)
        authService = service

        val completedIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val canceledIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        service.performAuthorizationRequest(
            authRepository.buildAuthRequest(),
            completedIntent,
            canceledIntent
        )
        _state.value = LoginState.Loading("Waiting for GitHub authorization...")
    }

    private fun handleAuthResponse(intent: Intent) {
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
            else -> _state.value = LoginState.Idle
        }
    }

    override fun onCleared() {
        authService?.dispose()
        super.onCleared()
    }
}
