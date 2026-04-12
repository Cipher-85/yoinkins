package com.apkpackager.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkpackager.data.github.model.WorkflowRunDto
import com.apkpackager.domain.DownloadState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildHistoryScreen(
    viewModel: BuildHistoryViewModel,
    owner: String,
    repo: String,
    onBack: () -> Unit,
    onRunSelected: (runId: Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloads by viewModel.downloads.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHistory(owner, repo) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Build History")
                        Text(
                            "$owner/$repo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(owner, repo) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is BuildHistoryState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is BuildHistoryState.Error -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(owner, repo) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text(s.message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.refresh(owner, repo) }) { Text("Retry") }
                        }
                    }
                }
            }
            is BuildHistoryState.Success -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(owner, repo) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    if (s.runs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No builds yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(s.runs, key = { it.id }) { run ->
                                BuildRunCard(
                                    run = run,
                                    onClick = { onRunSelected(run.id) },
                                    downloadState = downloads[run.id] ?: DownloadState.Idle,
                                    onDownload = { viewModel.downloadApk(owner, repo, run.id) },
                                    onInstall = { viewModel.installApk(run.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildRunCard(
    run: WorkflowRunDto,
    onClick: () -> Unit,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    val (statusIcon, statusColor) = when {
        run.status != "completed" -> Icons.Default.Pending to MaterialTheme.colorScheme.tertiary
        run.conclusion == "success" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        run.conclusion == "failure" -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
        run.conclusion == "cancelled" -> Icons.Default.Block to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    val statusLabel = when {
        run.status == "queued" -> "Queued"
        run.status == "in_progress" -> "In Progress"
        run.conclusion == "success" -> "Success"
        run.conclusion == "failure" -> "Failed"
        run.conclusion == "cancelled" -> "Cancelled"
        else -> run.conclusion ?: run.status
    }

    val isSuccess = run.status == "completed" && run.conclusion == "success"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(statusIcon, contentDescription = statusLabel, tint = statusColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${run.runNumber}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                        if (run.headBranch != null) {
                            Text(
                                " \u2022 ${run.headBranch}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        formatDate(run.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSuccess) {
                    when (downloadState) {
                        is DownloadState.Idle -> {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download APK",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is DownloadState.Downloading -> {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                        is DownloadState.Ready -> {
                            IconButton(onClick = onInstall) {
                                Icon(
                                    Icons.Default.InstallMobile,
                                    contentDescription = "Install APK",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is DownloadState.Error -> {
                            IconButton(onClick = onDownload) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Retry download",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))
                    context.startActivity(intent)
                }) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = "View on GitHub",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Download progress/error bar
            if (isSuccess && downloadState is DownloadState.Downloading) {
                LinearProgressIndicator(
                    progress = { downloadState.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isSuccess && downloadState is DownloadState.Error) {
                Text(
                    downloadState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val formatter = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        parser.parse(isoDate)?.let { formatter.format(it) } ?: isoDate
    } catch (_: Exception) {
        isoDate
    }
}
