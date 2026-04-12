package com.apkpackager.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.github.GitHubRepository
import com.apkpackager.data.github.model.WorkflowJobDto
import com.apkpackager.data.github.model.WorkflowRunDto
import com.apkpackager.domain.DownloadAndInstallUseCase
import com.apkpackager.domain.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BuildLogUiState(
    val run: WorkflowRunDto? = null,
    val jobs: List<WorkflowJobDto> = emptyList(),
    val isLoadingRun: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingLog: Boolean = false,
    val selectedJobId: Long? = null,
    val logContent: String? = null,
    val error: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val apkFile: File? = null
)

@HiltViewModel
class BuildLogViewModel @Inject constructor(
    private val githubRepository: GitHubRepository,
    private val downloadAndInstallUseCase: DownloadAndInstallUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BuildLogUiState())
    val state: StateFlow<BuildLogUiState> = _state

    fun load(owner: String, repo: String, runId: Long) {
        if (_state.value.run != null) return
        viewModelScope.launch {
            _state.value = BuildLogUiState(isLoadingRun = true)
            fetchRunDetails(owner, repo, runId)
        }
    }

    fun refresh(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, selectedJobId = null, logContent = null)
            fetchRunDetails(owner, repo, runId)
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    private suspend fun fetchRunDetails(owner: String, repo: String, runId: Long) {
        try {
            val run = githubRepository.getRun(owner, repo, runId)
            val jobs = githubRepository.getJobsForRun(owner, repo, runId)
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(
                run = run,
                jobs = jobs,
                isLoadingRun = false,
                error = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoadingRun = false,
                error = e.message ?: "Failed to load build details"
            )
        }
    }

    fun loadJobLog(owner: String, repo: String, jobId: Long) {
        if (_state.value.selectedJobId == jobId && _state.value.logContent != null) {
            _state.value = _state.value.copy(selectedJobId = null, logContent = null)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedJobId = jobId,
                isLoadingLog = true,
                logContent = null
            )
            githubRepository.getJobLog(owner, repo, jobId)
                .onSuccess { log ->
                    _state.value = _state.value.copy(
                        isLoadingLog = false,
                        logContent = log
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoadingLog = false,
                        logContent = "Failed to load logs: ${e.message}"
                    )
                }
        }
    }

    fun downloadApk(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            val artifacts = try {
                githubRepository.getArtifacts(owner, repo, runId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(downloadState = DownloadState.Error("Failed to list artifacts: ${e.message}"))
                return@launch
            }
            val artifact = artifacts.firstOrNull() ?: run {
                _state.value = _state.value.copy(downloadState = DownloadState.Error("No artifacts found for this build"))
                return@launch
            }
            downloadAndInstallUseCase.download(owner, repo, artifact.id) { downloadState ->
                _state.value = _state.value.copy(
                    downloadState = downloadState,
                    apkFile = if (downloadState is DownloadState.Ready) downloadState.apkFile else _state.value.apkFile
                )
            }
        }
    }

    fun installApk() {
        val apkFile = _state.value.apkFile ?: return
        if (!downloadAndInstallUseCase.canInstall()) {
            downloadAndInstallUseCase.openInstallSettings()
            return
        }
        downloadAndInstallUseCase.install(apkFile)
    }
}
