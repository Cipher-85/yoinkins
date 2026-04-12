package com.apkpackager.data.github.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepoDto(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerDto,
    @SerialName("private") val isPrivate: Boolean,
    @SerialName("default_branch") val defaultBranch: String
)

@Serializable
data class OwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String
)

@Serializable
data class BranchDto(val name: String)

@Serializable
data class FileEntryDto(
    val name: String,
    val path: String = "",
    val type: String,  // "file" or "dir"
    val sha: String
)

@Serializable
data class ContentDto(
    val sha: String,
    val content: String? = null
)

@Serializable
data class CreateFileRequest(
    val message: String,
    val content: String,
    val branch: String,
    val sha: String? = null
)

@Serializable
data class CreateFileResponse(val content: ContentDto)

@Serializable
data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRunDto>
)

@Serializable
data class WorkflowRunDto(
    val id: Long,
    val status: String,
    val conclusion: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class WorkflowJobsResponse(
    @SerialName("total_count") val totalCount: Int,
    val jobs: List<WorkflowJobDto>
)

@Serializable
data class WorkflowJobDto(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val steps: List<WorkflowStepDto>? = null
)

@Serializable
data class WorkflowStepDto(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int
)

@Serializable
data class ArtifactsResponse(
    @SerialName("total_count") val totalCount: Int,
    val artifacts: List<ArtifactDto>
)

@Serializable
data class ArtifactDto(
    val id: Long,
    val name: String,
    @SerialName("size_in_bytes") val sizeInBytes: Long
)
