package com.apkpackager.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.github.GitHubRepository
import com.apkpackager.data.github.model.WorkflowRunDto
import com.apkpackager.domain.DownloadAndInstallUseCase
import com.apkpackager.domain.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class BuildHistoryState {
    object Loading : BuildHistoryState()
    data class Success(val runs: List<WorkflowRunDto>) : BuildHistoryState()
    data class Error(val message: String) : BuildHistoryState()
}

@HiltViewModel
class BuildHistoryViewModel @Inject constructor(
    private val githubRepository: GitHubRepository,
    private val downloadAndInstallUseCase: DownloadAndInstallUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<BuildHistoryState>(BuildHistoryState.Loading)
    val state: StateFlow<BuildHistoryState> = _state

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _downloads = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Long, DownloadState>> = _downloads

    private val apkFiles = mutableMapOf<Long, File>()

    fun loadHistory(owner: String, repo: String) {
        if (_state.value is BuildHistoryState.Success) return
        viewModelScope.launch {
            _state.value = BuildHistoryState.Loading
            githubRepository.listBuildHistory(owner, repo)
                .onSuccess { runs ->
                    _state.value = BuildHistoryState.Success(runs)
                }
                .onFailure { e ->
                    _state.value = BuildHistoryState.Error(e.message ?: "Failed to load build history")
                }
        }
    }

    fun refresh(owner: String, repo: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            githubRepository.listBuildHistory(owner, repo)
                .onSuccess { runs ->
                    _state.value = BuildHistoryState.Success(runs)
                }
                .onFailure { e ->
                    _state.value = BuildHistoryState.Error(e.message ?: "Failed to load build history")
                }
            _isRefreshing.value = false
        }
    }

    fun downloadApk(owner: String, repo: String, runId: Long) {
        val current = _downloads.value[runId]
        if (current is DownloadState.Downloading) return
        viewModelScope.launch {
            val artifacts = try {
                githubRepository.getArtifacts(owner, repo, runId)
            } catch (e: Exception) {
                _downloads.value = _downloads.value + (runId to DownloadState.Error("Failed to list artifacts: ${e.message}"))
                return@launch
            }
            val artifact = artifacts.firstOrNull() ?: run {
                _downloads.value = _downloads.value + (runId to DownloadState.Error("No artifacts found"))
                return@launch
            }
            downloadAndInstallUseCase.download(owner, repo, artifact.id) { downloadState ->
                _downloads.value = _downloads.value + (runId to downloadState)
                if (downloadState is DownloadState.Ready) {
                    apkFiles[runId] = downloadState.apkFile
                }
            }
        }
    }

    fun installApk(runId: Long) {
        val apkFile = apkFiles[runId] ?: return
        if (!downloadAndInstallUseCase.canInstall()) {
            downloadAndInstallUseCase.openInstallSettings()
            return
        }
        downloadAndInstallUseCase.install(apkFile)
    }
}
