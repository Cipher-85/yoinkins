package com.apkpackager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apkpackager.data.auth.AuthRedirectBus
import com.apkpackager.data.auth.TokenStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRedirectBus: AuthRedirectBus
    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchOAuthRedirect(intent)
        val isLoggedIn = tokenStore.hasToken()
        setContent {
            YoinkinsTheme {
                AppNavGraph(isLoggedIn = isLoggedIn)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchOAuthRedirect(intent)
    }

    private fun dispatchOAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "apkpackager" && data.host == "oauth") {
            authRedirectBus.publish(data)
        }
    }
}
