package com.apkpackager.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkpackager.data.github.model.WorkflowJobDto
import com.apkpackager.data.github.model.WorkflowStepDto
import com.apkpackager.domain.DownloadState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildLogScreen(
    viewModel: BuildLogViewModel,
    owner: String,
    repo: String,
    runId: Long,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load(owner, repo, runId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.run != null) "Build #${state.run!!.runNumber}" else "Build Details"
                        )
                        if (state.run?.headBranch != null) {
                            Text(
                                state.run!!.headBranch!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.run != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.run!!.htmlUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "View on GitHub")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoadingRun -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh(owner, repo, runId) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh(owner, repo, runId) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Run summary card
                    state.run?.let { run ->
                        item(key = "summary") {
                            RunSummaryCard(run)
                        }
                    }

                    // Jobs section
                    item(key = "jobs_header") {
                        Text(
                            "Jobs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (state.jobs.isEmpty()) {
                        item(key = "no_jobs") {
                            Text(
                                "No jobs found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.jobs, key = { it.id }) { job ->
                            JobCard(
                                job = job,
                                isSelected = state.selectedJobId == job.id,
                                isLoadingLog = state.isLoadingLog && state.selectedJobId == job.id,
                                logContent = if (state.selectedJobId == job.id) state.logContent else null,
                                onToggleLog = { viewModel.loadJobLog(owner, repo, job.id) }
                            )
                        }
                    }

                    // Download APK section for successful builds
                    if (state.run?.conclusion == "success") {
                        item(key = "download") {
                            Spacer(Modifier.height(8.dp))
                            DownloadApkCard(
                                downloadState = state.downloadState,
                                onDownload = { viewModel.downloadApk(owner, repo, runId) },
                                onInstall = { viewModel.installApk() }
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
private fun RunSummaryCard(run: com.apkpackager.data.github.model.WorkflowRunDto) {
    val (statusLabel, statusColor) = when {
        run.status != "completed" -> (if (run.status == "in_progress") "In Progress" else "Queued") to MaterialTheme.colorScheme.tertiary
        run.conclusion == "success" -> "Success" to MaterialTheme.colorScheme.primary
        run.conclusion == "failure" -> "Failed" to MaterialTheme.colorScheme.error
        run.conclusion == "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> (run.conclusion ?: run.status) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", style = MaterialTheme.typography.bodyMedium)
                Text(statusLabel, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Started: ${formatDate(run.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (run.updatedAt != null) {
                Text(
                    "Updated: ${formatDate(run.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun JobCard(
    job: WorkflowJobDto,
    isSelected: Boolean,
    isLoadingLog: Boolean,
    logContent: String?,
    onToggleLog: () -> Unit
) {
    val (statusIcon, statusColor) = when {
        job.status != "completed" -> Icons.Default.Pending to MaterialTheme.colorScheme.tertiary
        job.conclusion == "success" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        job.conclusion == "failure" -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
        else -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleLog)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(job.name, style = MaterialTheme.typography.titleSmall)
                    if (job.startedAt != null && job.completedAt != null) {
                        Text(
                            formatDuration(job.startedAt, job.completedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Steps summary
                job.steps?.let { steps ->
                    val passed = steps.count { it.conclusion == "success" }
                    Text(
                        "$passed/${steps.size} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isSelected) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Steps list (always shown when expanded)
            if (isSelected) {
                job.steps?.let { steps ->
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Steps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        steps.forEach { step ->
                            StepRow(step)
                        }
                    }
                }

                // Log content
                HorizontalDivider()
                if (isLoadingLog) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (logContent != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                                .heightIn(max = 400.dp)
                        ) {
                            Text(
                                text = logContent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: WorkflowStepDto) {
    val (icon, color) = when (step.conclusion) {
        "success" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        "failure" -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
        "skipped" -> Icons.Default.SkipNext to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Default.Pending to MaterialTheme.colorScheme.tertiary
    }
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(step.name, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun DownloadApkCard(
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("APK Artifact", style = MaterialTheme.typography.titleMedium)
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

private fun formatDuration(startIso: String, endIso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val start = parser.parse(startIso)?.time ?: return ""
        val end = parser.parse(endIso)?.time ?: return ""
        val seconds = (end - start) / 1000
        when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    } catch (_: Exception) {
        ""
    }
}
