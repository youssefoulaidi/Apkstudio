package com.apkstudio.app.data.github

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface GitHubApi {

    @GET("user")
    suspend fun getUser(): GitHubUser

    @GET("user/repos")
    suspend fun listRepos(
        @Query("type") type: String = "all",
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<Repo>

    @POST("user/repos")
    suspend fun createRepo(@Body body: CreateRepoRequest): Repo

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Repo

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "branch", encoded = true) branch: String
    ): GitRef

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateBlobRequest
    ): BlobResult

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateTreeRequest
    ): TreeResult

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateCommitRequest
    ): CommitResult

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest
    ): GitRef

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "branch", encoded = true) branch: String,
        @Body body: UpdateRefRequest
    ): GitRef

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "workflow", encoded = true) workflow: String,
        @Body body: WorkflowDispatchRequest
    ): Response<Void>

    @GET("repos/{owner}/{repo}/actions/workflows/{workflow}/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "workflow", encoded = true) workflow: String,
        @Query("per_page") perPage: Int = 10
    ): RunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{runId}")
    suspend fun getRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): WorkflowRun

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/jobs")
    suspend fun listJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): JobsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/logs")
    @Streaming
    suspend fun downloadRunLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): ResponseBody

    @GET("repos/{owner}/{repo}/actions/runs/{runId}/artifacts")
    suspend fun listArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("runId") runId: Long
    ): ArtifactsResponse

    @GET("repos/{owner}/{repo}/actions/artifacts/{artifactId}/zip")
    @Streaming
    suspend fun downloadArtifactZip(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifactId") artifactId: Long
    ): ResponseBody
}
