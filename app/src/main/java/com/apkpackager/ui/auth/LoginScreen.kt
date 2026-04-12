package com.apkpackager.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Navigate on success
    LaunchedEffect(state) {
        if (state is LoginState.Success) onLoggedIn()
    }

    // Launch OAuth intent; AppAuth delivers the response via the result intent
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { viewModel.handleAuthResponse(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Forgewright", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Build and install APKs from your GitHub repos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        when (val s = state) {
            is LoginState.Idle, is LoginState.Error -> {
                Button(
                    onClick = { viewModel.startLogin { launcher.launch(it) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with GitHub")
                }
                if (s is LoginState.Error) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is LoginState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(s.message, style = MaterialTheme.typography.bodySmall)
            }
            is LoginState.Success -> {
                CircularProgressIndicator()
            }
        }
    }
}
