package com.apkpackager.data.github

import com.apkpackager.data.github.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GitHubApiService {

    @GET("user/repos")
    suspend fun listRepos(
        @Query("affiliation") affiliation: String = "owner,collaborator",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "updated"
    ): Response<List<RepoDto>>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun listBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100
    ): Response<List<BranchDto>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String
    ): Response<ContentDto>

    @GET("repos/{owner}/{repo}/contents/")
    suspend fun getRootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String
    ): Response<List<FileEntryDto>>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: CreateFileRequest
    ): CreateFileResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun triggerWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body body: WorkflowDispatchRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("workflow_id") workflowId: String = "build-apk.yml",
        @Query("branch") branch: String,
        @Query("per_page") perPage: Int = 5
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowRunDto

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun listArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): ArtifactsResponse

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listAllWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun listWorkflowJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowJobsResponse

    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun downloadJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): Response<ResponseBody>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/artifacts/{artifact_id}/zip")
    suspend fun downloadArtifact(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifact_id") artifactId: Long
    ): Response<ResponseBody>
}
