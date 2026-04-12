package com.apkpackager.ui.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.github.GitHubRepository
import com.apkpackager.domain.BuildStep
import com.apkpackager.domain.DownloadAndInstallUseCase
import com.apkpackager.domain.DownloadState
import com.apkpackager.domain.TriggerBuildUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BuildUiState(
    val steps: List<StepUiItem> = emptyList(),
    val currentStep: BuildStep? = null,
    val runHtmlUrl: String? = null,
    val runId: Long? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val apkFile: File? = null,
    val needsInstallPermission: Boolean = false
)

data class StepUiItem(val label: String, val status: StepStatus)

enum class StepStatus { PENDING, IN_PROGRESS, DONE, ERROR }

@HiltViewModel
class BuildDashboardViewModel @Inject constructor(
    private val triggerBuildUseCase: TriggerBuildUseCase,
    private val downloadAndInstallUseCase: DownloadAndInstallUseCase,
    private val githubRepository: GitHubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuildUiState())
    val state: StateFlow<BuildUiState> = _state

    fun startBuild(owner: String, repo: String, branch: String) {
        if (_state.value.steps.isNotEmpty()) return // already started
        viewModelScope.launch {
            triggerBuildUseCase.execute(owner, repo, branch) { step ->
                updateFromStep(step)
            }
        }
    }

    private fun updateFromStep(step: BuildStep) {
        val current = _state.value
        val steps = current.steps.toMutableList()

        fun upsert(label: String, status: StepStatus) {
            val idx = steps.indexOfFirst { it.label == label }
            if (idx >= 0) steps[idx] = StepUiItem(label, status)
            else steps.add(StepUiItem(label, status))
        }

        when (step) {
            is BuildStep.DetectingFramework -> upsert("Detecting framework", StepStatus.IN_PROGRESS)
            is BuildStep.FrameworkDetected -> {
                upsert("Detecting framework", StepStatus.DONE)
                upsert("Framework: ${step.framework.name}", StepStatus.DONE)
            }
            is BuildStep.CheckingWorkflow -> upsert("Setting up workflow", StepStatus.IN_PROGRESS)
            is BuildStep.WorkflowReady -> upsert("Setting up workflow", StepStatus.DONE)
            is BuildStep.Triggering -> upsert("Triggering build", StepStatus.IN_PROGRESS)
            is BuildStep.Queued -> {
                upsert("Triggering build", StepStatus.DONE)
                upsert("Build queued", StepStatus.IN_PROGRESS)
            }
            is BuildStep.InProgress -> {
                upsert("Build queued", StepStatus.DONE)
                upsert("Build in progress", StepStatus.IN_PROGRESS)
            }
            is BuildStep.Success -> {
                upsert("Build in progress", StepStatus.DONE)
                upsert("Build complete", StepStatus.DONE)
            }
            is BuildStep.Failure -> {
                upsert("Build failed (${step.conclusion ?: "unknown"})", StepStatus.ERROR)
            }
            is BuildStep.Error -> {
                upsert("Error: ${step.message}", StepStatus.ERROR)
            }
        }

        val runId = when (step) {
            is BuildStep.Queued -> step.runId
            is BuildStep.InProgress -> step.runId
            is BuildStep.Success -> step.runId
            is BuildStep.Failure -> step.runId
            else -> current.runId
        }
        val htmlUrl = when (step) {
            is BuildStep.InProgress -> step.htmlUrl
            is BuildStep.Success -> step.htmlUrl
            is BuildStep.Failure -> step.htmlUrl
            else -> current.runHtmlUrl
        }

        _state.value = current.copy(steps = steps, currentStep = step, runId = runId, runHtmlUrl = htmlUrl)
    }

    fun downloadApk(owner: String, repo: String) {
        val runId = _state.value.runId ?: return
        viewModelScope.launch {
            val artifacts = githubRepository.getArtifacts(owner, repo, runId)
            val artifact = artifacts.firstOrNull() ?: run {
                _state.value = _state.value.copy(downloadState = DownloadState.Error("No artifacts found"))
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
            _state.value = _state.value.copy(needsInstallPermission = true)
            downloadAndInstallUseCase.openInstallSettings()
            return
        }
        downloadAndInstallUseCase.install(apkFile)
    }

    fun onReturnFromSettings() {
        _state.value = _state.value.copy(needsInstallPermission = false)
        installApk()
    }
}
