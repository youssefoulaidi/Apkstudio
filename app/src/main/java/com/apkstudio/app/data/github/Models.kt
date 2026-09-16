package com.apkstudio.app.data.github

import com.squareup.moshi.Json

// ---------- User & repos ----------

data class GitHubUser(
    val id: Long = 0,
    val login: String = "",
    val name: String? = null,
    val email: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

data class Owner(
    val login: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

data class RepoPermissions(
    val admin: Boolean = false,
    val push: Boolean = false
)

data class Repo(
    val id: Long = 0,
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val private: Boolean = true,
    val description: String? = null,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "html_url") val htmlUrl: String = "",
    val owner: Owner = Owner(),
    val permissions: RepoPermissions? = null,
    val size: Int = 0,
    @Json(name = "updated_at") val updatedAt: String? = null,
    val language: String? = null
)

data class CreateRepoRequest(
    val name: String,
    val private: Boolean,
    val description: String = "",
    @Json(name = "auto_init") val autoInit: Boolean = false
)

// ---------- Git database API ----------

data class GitObject(
    val sha: String = ""
)

data class GitRef(
    val ref: String = "",
    @Json(name = "object") val gitObject: GitObject = GitObject()
)

data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

data class BlobResult(
    val sha: String = ""
)

data class TreeEntry(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String
)

data class CreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String? = null,
    val tree: List<TreeEntry>
)

data class TreeResult(
    val sha: String = ""
)

data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String> = emptyList()
)

data class CommitResult(
    val sha: String = ""
)

data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)

data class CreateRefRequest(
    val ref: String,
    val sha: String
)

// ---------- Actions ----------

data class WorkflowDispatchRequest(
    val ref: String
)

data class WorkflowRun(
    val id: Long = 0,
    val name: String? = null,
    @Json(name = "head_sha") val headSha: String? = null,
    @Json(name = "head_branch") val headBranch: String? = null,
    val status: String? = null, // queued | in_progress | completed
    val conclusion: String? = null, // success | failure | cancelled | timed_out ...
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "run_number") val runNumber: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

data class RunsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "workflow_runs") val runs: List<WorkflowRun> = emptyList()
)

data class StepInfo(
    val name: String = "",
    val status: String? = null, // queued | in_progress | completed
    val conclusion: String? = null,
    val number: Int = 0
)

data class JobInfo(
    val id: Long = 0,
    @Json(name = "run_id") val runId: Long = 0,
    val name: String = "",
    val status: String? = null,
    val conclusion: String? = null,
    val steps: List<StepInfo> = emptyList(),
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null
)

data class JobsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    val jobs: List<JobInfo> = emptyList()
)

data class ArtifactInfo(
    val id: Long = 0,
    val name: String = "",
    @Json(name = "size_in_bytes") val sizeInBytes: Long = 0,
    val expired: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null
)

data class ArtifactsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    val artifacts: List<ArtifactInfo> = emptyList()
)
