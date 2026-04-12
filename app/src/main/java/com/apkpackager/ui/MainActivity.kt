package com.apkpackager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apkpackager.data.auth.AuthResultBus
import com.apkpackager.data.auth.TokenStore
import dagger.hilt.android.AndroidEntryPoint
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var authResultBus: AuthResultBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchAuthResult(intent)
        val isLoggedIn = tokenStore.hasToken()
        setContent {
            APKPackagerTheme {
                AppNavGraph(isLoggedIn = isLoggedIn)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchAuthResult(intent)
    }

    private fun dispatchAuthResult(intent: Intent?) {
        intent ?: return
        if (AuthorizationResponse.fromIntent(intent) != null ||
            AuthorizationException.fromIntent(intent) != null) {
            authResultBus.publish(intent)
        }
    }
}
