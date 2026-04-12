package com.apkpackager.ui.build

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkpackager.domain.BuildStep
import com.apkpackager.domain.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildDashboardScreen(
    viewModel: BuildDashboardViewModel,
    owner: String,
    repo: String,
    branch: String,
    onBack: () -> Unit,
    onHistory: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Settings return launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onReturnFromSettings() }

    LaunchedEffect(Unit) { viewModel.startBuild(owner, repo, branch) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(repo)
                    Text(branch, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = "Build History")
                    }
                    if (state.runHtmlUrl != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.runHtmlUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "View on GitHub")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.steps) { step ->
                StepCard(step)
            }

            // Download / Install section
            if (state.currentStep is BuildStep.Success) {
                item {
                    Spacer(Modifier.height(8.dp))
                    DownloadInstallSection(
                        downloadState = state.downloadState,
                        onDownload = { viewModel.downloadApk(owner, repo) },
                        onInstall = { viewModel.installApk() }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: StepUiItem) {
    val context = LocalContext.current
    val (icon, tint) = when (step.status) {
        StepStatus.PENDING -> Icons.Default.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
        StepStatus.IN_PROGRESS -> Icons.Default.Pending to MaterialTheme.colorScheme.primary
        StepStatus.DONE -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        StepStatus.ERROR -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step.status == StepStatus.IN_PROGRESS) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(step.label, modifier = Modifier.weight(1f))
            if (step.status == StepStatus.ERROR) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Build Error", step.label))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadInstallSection(
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("APK Ready", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            when (downloadState) {
                is DownloadState.Idle -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download APK")
                    }
                }
                is DownloadState.Downloading -> {
                    Text("Downloading... ${downloadState.progressPercent}%")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is DownloadState.Ready -> {
                    Text("Downloaded", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install APK")
                    }
                }
                is DownloadState.Error -> {
                    Text(downloadState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}
