package com.apkpackager.data.auth

import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRedirectBus @Inject constructor() {
    private val _redirects = Channel<Uri>(capacity = 1)
    val redirects = _redirects.receiveAsFlow()

    fun publish(uri: Uri) {
        _redirects.trySend(uri)
    }
}
