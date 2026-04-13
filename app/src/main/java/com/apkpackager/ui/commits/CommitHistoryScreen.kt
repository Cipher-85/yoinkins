package com.apkpackager.ui.commits

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
import com.apkpackager.data.github.model.CommitDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitHistoryScreen(
    viewModel: CommitHistoryViewModel,
    owner: String,
    repo: String,
    branch: String,
    onCommitSelected: (sha: String, message: String) -> Unit,
    onBack: () -> Unit,
    onHistory: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastKnownGoodSha by viewModel.lastKnownGoodSha.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(owner, repo, branch) { viewModel.loadCommits(owner, repo, branch) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(repo)
                        Text(
                            branch,
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
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = "Build History")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is CommitListState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is CommitListState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadCommits(owner, repo, branch) }) {
                        Text("Retry")
                    }
                }
            }

            is CommitListState.Success -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(owner, repo, branch) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    LazyColumn {
                        items(s.commits, key = { it.sha }) { commit ->
                            CommitCard(
                                commit = commit,
                                isLastKnownGood = commit.sha == lastKnownGoodSha,
                                onBuild = { onCommitSelected(commit.sha, firstLine(commit.commit.message)) },
                                onToggleLastKnownGood = {
                                    viewModel.toggleLastKnownGood(owner, repo, branch, commit.sha)
                                },
                                onOpenInBrowser = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(commit.htmlUrl))
                                    )
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitCard(
    commit: CommitDto,
    isLastKnownGood: Boolean,
    onBuild: () -> Unit,
    onToggleLastKnownGood: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onBuild),
        overlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    commit.sha.take(7),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                commit.commit.author?.date?.let { date ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatDate(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLastKnownGood) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Last Known Good",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                firstLine(commit.commit.message),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            commit.commit.author?.name?.let { name ->
                Text(name, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onToggleLastKnownGood) {
                    Icon(
                        if (isLastKnownGood) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isLastKnownGood) "Unmark last known good" else "Mark as last known good",
                        tint = if (isLastKnownGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onBuild) {
                    Icon(
                        Icons.Default.RocketLaunch,
                        contentDescription = "Build this commit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onOpenInBrowser) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = "View on GitHub",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}

private fun firstLine(message: String): String =
    message.lineSequence().firstOrNull()?.trim() ?: message.trim()

private fun formatDate(isoDate: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoDate) ?: return isoDate
        val outFmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
        outFmt.format(date)
    } catch (_: Exception) {
        isoDate
    }
}
