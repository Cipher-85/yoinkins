package com.apkpackager.data.auth

import android.content.Context
import android.net.Uri
import com.apkpackager.BuildConfig
import net.openid.appauth.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class AuthRepository @Inject constructor(
    private val tokenStore: TokenStore
) {
    val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://github.com/login/oauth/authorize"),
        Uri.parse("https://github.com/login/oauth/access_token")
    )

    fun buildAuthRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.GITHUB_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse("apkpackager://oauth/callback")
        )
            .setScope("repo workflow")
            .build()
    }

    suspend fun exchangeCode(
        context: Context,
        response: AuthorizationResponse
    ): String = suspendCoroutine { cont ->
        val service = AuthorizationService(context)
        val tokenRequest = response.createTokenExchangeRequest(
            mapOf("client_secret" to BuildConfig.GITHUB_CLIENT_SECRET)
        )
        service.performTokenRequest(tokenRequest) { tokenResponse, ex ->
            service.dispose()
            when {
                tokenResponse?.accessToken != null -> {
                    val token = tokenResponse.accessToken!!
                    tokenStore.saveToken(token)
                    cont.resume(token)
                }
                ex != null -> cont.resumeWithException(ex)
                else -> cont.resumeWithException(IllegalStateException("Token exchange failed"))
            }
        }
    }

    fun isLoggedIn(): Boolean = tokenStore.hasToken()

    fun logout() = tokenStore.clearToken()
}
