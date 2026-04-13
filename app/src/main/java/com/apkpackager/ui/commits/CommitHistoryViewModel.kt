package com.apkpackager.ui.commits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkpackager.data.LastKnownGoodStore
import com.apkpackager.data.github.GitHubRepository
import com.apkpackager.data.github.model.CommitDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CommitListState {
    object Loading : CommitListState()
    data class Success(val commits: List<CommitDto>) : CommitListState()
    data class Error(val message: String) : CommitListState()
}

@HiltViewModel
class CommitHistoryViewModel @Inject constructor(
    private val githubRepository: GitHubRepository,
    private val lastKnownGoodStore: LastKnownGoodStore
) : ViewModel() {

    private val _state = MutableStateFlow<CommitListState>(CommitListState.Loading)
    val state: StateFlow<CommitListState> = _state

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _lastKnownGoodSha = MutableStateFlow<String?>(null)
    val lastKnownGoodSha: StateFlow<String?> = _lastKnownGoodSha

    fun loadCommits(owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            _state.value = CommitListState.Loading
            fetchCommits(owner, repo, branch)
        }
        observeLastKnownGood(owner, repo, branch)
    }

    fun refresh(owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchCommits(owner, repo, branch)
            _isRefreshing.value = false
        }
    }

    fun toggleLastKnownGood(owner: String, repo: String, branch: String, sha: String) {
        viewModelScope.launch {
            if (_lastKnownGoodSha.value == sha) {
                lastKnownGoodStore.clear(owner, repo, branch)
            } else {
                lastKnownGoodStore.set(owner, repo, branch, sha)
            }
        }
    }

    private fun observeLastKnownGood(owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            lastKnownGoodStore.observe(owner, repo, branch).collect {
                _lastKnownGoodSha.value = it
            }
        }
    }

    private suspend fun fetchCommits(owner: String, repo: String, branch: String) {
        val result = githubRepository.listCommits(owner, repo, branch)
        _state.value = if (result.isSuccess) {
            CommitListState.Success(result.getOrDefault(emptyList()))
        } else {
            CommitListState.Error(result.exceptionOrNull()?.message ?: "Failed to load commits")
        }
    }
}
