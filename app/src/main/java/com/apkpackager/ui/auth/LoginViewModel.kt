package com.apkpackager.ui.auth

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.auth.AuthRedirectBus
import com.apkpackager.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
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
    private val authRedirectBus: AuthRedirectBus
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LoginState>(
        if (authRepository.isLoggedIn()) LoginState.Success else LoginState.Idle
    )
    val state: StateFlow<LoginState> = _state

    private var pendingRequest: AuthorizationRequest? = null

    init {
        viewModelScope.launch {
            authRedirectBus.redirects.collect { uri -> handleRedirect(uri) }
        }
    }

    fun startLogin(context: Context) {
        val request = authRepository.buildAuthRequest()
        pendingRequest = request
        _state.value = LoginState.Loading("Waiting for GitHub authorization...")
        val tab = CustomTabsIntent.Builder().build()
        tab.launchUrl(context, request.toUri())
    }

    private fun handleRedirect(uri: Uri) {
        val request = pendingRequest ?: authRepository.buildAuthRequest()
        val error = uri.getQueryParameter("error")
        if (error != null) {
            val desc = uri.getQueryParameter("error_description") ?: error
            _state.value = LoginState.Error(desc)
            pendingRequest = null
            return
        }
        if (uri.getQueryParameter("code") == null) return

        _state.value = LoginState.Loading("Exchanging token...")
        viewModelScope.launch {
            try {
                val response = authRepository.responseFromRedirect(request, uri)
                authRepository.exchangeCode(getApplication(), response)
                _state.value = LoginState.Success
            } catch (e: Exception) {
                _state.value = LoginState.Error(e.message ?: "Token exchange failed")
            } finally {
                pendingRequest = null
            }
        }
    }
}
