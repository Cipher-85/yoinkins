package com.apkpackager.data.auth

import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthResultBus @Inject constructor() {
    private val _results = Channel<Intent>(capacity = 1)
    val results = _results.receiveAsFlow()

    fun publish(intent: Intent) {
        _results.trySend(intent)
    }
}
