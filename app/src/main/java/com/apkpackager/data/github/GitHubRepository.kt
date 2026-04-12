package com.apkpackager.data.github

import android.util.Base64
import com.apkpackager.data.github.model.*
import com.apkpackager.data.workflow.AppFramework
import com.apkpackager.data.workflow.WorkflowTemplateProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepository @Inject constructor(
    private val api: GitHubApiService
) {
    suspend fun listRepos(page: Int = 1): Result<List<RepoDto>> {
        return try {
            val response = api.listRepos(page = page)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load repos: HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listBranches(owner: String, repo: String): Result<List<BranchDto>> {
        return try {
            val response = api.listBranches(owner, repo)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load branches: HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureWorkflow(owner: String, repo: String, branch: String, framework: AppFramework): Result<Unit> {
        val existingSha = try {
            val existingResponse = api.getContents(owner, repo, ".github/workflows/build-apk.yml", branch)
            if (existingResponse.isSuccessful) existingResponse.body()?.sha else null
        } catch (e: Exception) {
            return Result.failure(Exception("checking existing workflow file failed: ${e.message ?: e.javaClass.simpleName}", e))
        }

        return try {
            val yaml = WorkflowTemplateProvider.getTemplate(framework)
            val encoded = Base64.encodeToString(yaml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            api.createOrUpdateFile(
                owner, repo, ".github/workflows/build-apk.yml",
                CreateFileRequest(
                    message = "chore: add APK build workflow",
                    content = encoded,
                    branch = branch,
                    sha = existingSha
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("committing workflow file failed: ${e.message ?: e.javaClass.simpleName}", e))
        }
    }

    suspend fun triggerBuild(owner: String, repo: String, branch: String): Result<Unit> {
        return try {
            val response = api.triggerWorkflow(
                owner, repo, "build-apk.yml",
                WorkflowDispatchRequest(ref = branch)
            )
            if (response.isSuccessful || response.code() == 204) Result.success(Unit)
            else Result.failure(Exception("Trigger failed: HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findRunAfter(owner: String, repo: String, branch: String, afterEpochMs: Long): WorkflowRunDto? {
        val runs = api.listWorkflowRuns(owner, repo, branch = branch).workflowRuns
        return runs.firstOrNull { run ->
            parseIso8601(run.createdAt) >= afterEpochMs
        }
    }

    suspend fun getRun(owner: String, repo: String, runId: Long): WorkflowRunDto =
        api.getWorkflowRun(owner, repo, runId)

    suspend fun getArtifacts(owner: String, repo: String, runId: Long): List<ArtifactDto> =
        api.listArtifacts(owner, repo, runId).artifacts

    suspend fun listBuildHistory(owner: String, repo: String, page: Int = 1): Result<List<WorkflowRunDto>> {
        return try {
            val response = api.listAllWorkflowRuns(owner, repo, page = page)
            Result.success(response.workflowRuns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getJobsForRun(owner: String, repo: String, runId: Long): Result<List<WorkflowJobDto>> {
        return try {
            val response = api.listWorkflowJobs(owner, repo, runId)
            Result.success(response.jobs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getJobLog(owner: String, repo: String, jobId: Long): Result<String> {
        return try {
            val response = api.downloadJobLogs(owner, repo, jobId)
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: ""
                Result.success(body)
            } else {
                Result.failure(Exception("Failed to fetch logs: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getArtifactDownloadUrl(owner: String, repo: String, artifactId: Long): String =
        "https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId/zip"

    private fun parseIso8601(dateStr: String): Long {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }
}
