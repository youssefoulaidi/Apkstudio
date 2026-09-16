package com.apkstudio.app.data.github

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.data.project.PushFile
import com.apkstudio.app.data.project.WorkflowGenerator
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.ErrorMapper
import com.apkstudio.app.util.FailureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class PushResult(
    val commitSha: String,
    val filesPushed: Int
)

class GitHubRepository(private val appContext: Context) {

    private fun api(): GitHubApi {
        val token = AppGraph.session.token()
            ?: throw FailureException(
                AppFailure(
                    appContext.getString(R.string.err_401_title),
                    appContext.getString(R.string.err_401_hint),
                    null
                )
            )
        return GitHubClient.api(token)
    }

    private suspend fun <T> guard(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(FailureException(ErrorMapper.fromThrowable(appContext, t)))
        }
    }

    // ---------- Auth ----------

    suspend fun validateToken(token: String): Result<GitHubUser> = guard {
        withContext(Dispatchers.IO) {
            GitHubClient.api(token.trim()).getUser()
        }
    }

    // ---------- Repos ----------

    suspend fun listRepos(): Result<List<Repo>> = guard {
        withContext(Dispatchers.IO) {
            val all = mutableListOf<Repo>()
            var page = 1
            while (page <= 5) {
                val batch = api().listRepos(page = page)
                all += batch
                if (batch.size < 100) break
                page++
            }
            all
        }
    }

    suspend fun createRepo(name: String, private: Boolean): Result<Repo> = guard {
        withContext(Dispatchers.IO) {
            api().createRepo(CreateRepoRequest(name.trim(), private))
        }
    }

    suspend fun refreshRepo(owner: String, name: String): Result<Repo> = guard {
        withContext(Dispatchers.IO) { api().getRepo(owner, name) }
    }

    // ---------- Push ----------

    suspend fun pushProject(
        owner: String,
        repo: String,
        branch: String,
        files: List<PushFile>,
        workflowYaml: String,
        message: String,
        onProgress: (done: Int, total: Int, currentPath: String) -> Unit
    ): Result<PushResult> = guard {
        withContext(Dispatchers.IO) {
            val gh = api()

            // Base commit (null for empty repos / missing branch).
            var baseSha: String? = null
            try {
                baseSha = gh.getBranchRef(owner, repo, branch).gitObject.sha
            } catch (e: HttpException) {
                if (e.code() != 404 && e.code() != 409) throw e
            }

            val total = files.size + 1
            val shas = LinkedHashMap<String, String>()

            files.forEachIndexed { index, pushFile ->
                onProgress(index, total, pushFile.path)
                val bytes = pushFile.file.readBytes()
                val content = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val sha = gh.createBlob(owner, repo, CreateBlobRequest(content)).sha
                shas[pushFile.path] = sha
                onProgress(index + 1, total, pushFile.path)
            }

            onProgress(files.size, total, WorkflowGenerator.PATH)
            val workflowSha = gh.createBlob(
                owner, repo,
                CreateBlobRequest(
                    Base64.encodeToString(
                        workflowYaml.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                )
            ).sha
            onProgress(total, total, WorkflowGenerator.PATH)

            val entries = shas.map { (path, sha) ->
                val exec = files.firstOrNull { it.path == path }?.executable == true
                TreeEntry(path, if (exec) "100755" else "100644", "blob", sha)
            } + TreeEntry(WorkflowGenerator.PATH, "100644", "blob", workflowSha)

            val tree = gh.createTree(owner, repo, CreateTreeRequest(baseSha, entries))
            val commit = gh.createCommit(
                owner, repo,
                CreateCommitRequest(
                    message,
                    tree.sha,
                    if (baseSha != null) listOf(baseSha) else emptyList()
                )
            )

            if (baseSha != null) {
                gh.updateRef(owner, repo, branch, UpdateRefRequest(commit.sha))
            } else {
                gh.createRef(owner, repo, CreateRefRequest("refs/heads/$branch", commit.sha))
            }

            PushResult(commit.sha, files.size)
        }
    }

    // ---------- Build ----------

    suspend fun dispatchBuild(owner: String, repo: String, branch: String): Result<Unit> =
        guard {
            withContext(Dispatchers.IO) {
                val res = api().dispatchWorkflow(
                    owner, repo,
                    WorkflowGenerator.FILE_NAME,
                    WorkflowDispatchRequest(branch)
                )
                if (!res.isSuccessful) throw HttpException(res)
            }
        }

    suspend fun findRunBySha(
        owner: String,
        repo: String,
        headSha: String,
        timeoutMs: Long = 150_000
    ): Result<WorkflowRun> = guard {
        withContext(Dispatchers.IO) {
            val gh = api()
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < timeoutMs) {
                val runs = gh.listWorkflowRuns(owner, repo, WorkflowGenerator.FILE_NAME, 10).runs
                val match = runs.firstOrNull {
                    it.headSha?.equals(headSha, ignoreCase = true) == true
                }
                if (match != null) return@withContext match
                delay(8000)
            }
            throw FailureException(
                AppFailure(
                    appContext.getString(R.string.err_run_not_found_title),
                    appContext.getString(R.string.err_run_not_found_hint),
                    appContext.getString(R.string.err_run_not_found_detail)
                )
            )
        }
    }

    suspend fun getRun(owner: String, repo: String, runId: Long): Result<WorkflowRun> =
        guard {
            withContext(Dispatchers.IO) { api().getRun(owner, repo, runId) }
        }

    suspend fun getJobs(owner: String, repo: String, runId: Long): Result<List<JobInfo>> =
        guard {
            withContext(Dispatchers.IO) { api().listJobs(owner, repo, runId).jobs }
        }

    /**
     * Downloads run logs. GitHub answers with a redirect to either a zip
     * archive or a plain-text log — both are handled.
     */
    suspend fun getLogs(owner: String, repo: String, runId: Long): Result<String> =
        guard {
            withContext(Dispatchers.IO) {
                val body = api().downloadRunLogs(owner, repo, runId)
                body.use { response ->
                    val bytes = response.byteStream().use { it.readBytesCapped(MAX_LOG_BYTES) }
                    decodeLogs(bytes)
                }
            }
        }

    private fun decodeLogs(bytes: ByteArray): String {
        val text = if (bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            // Zip archive: concatenate text entries.
            val sb = StringBuilder()
            try {
                ZipInputStream(bytes.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    var count = 0
                    while (entry != null && count < 40 && sb.length < MAX_LOG_CHARS * 2) {
                        if (!entry.isDirectory && entry.size < 600_000) {
                            val out = ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            var n: Int
                            var entryBytes = 0
                            while (zip.read(buf).also { n = it } != -1 && entryBytes < 600_000) {
                                out.write(buf, 0, n)
                                entryBytes += n
                            }
                            val name = entry.name.substringAfterLast('/').substringBeforeLast('.')
                            sb.append("\n===== ").append(name).append(" =====\n")
                            sb.append(out.toString(Charsets.UTF_8.name()))
                            count++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (_: Exception) {
            }
            sb.toString()
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        if (text.isBlank()) return ""
        // Keep the tail: the most relevant part of long logs.
        return if (text.length > MAX_LOG_CHARS) {
            "…\n" + text.takeLast(MAX_LOG_CHARS)
        } else {
            text
        }
    }

    // ---------- Artifacts ----------

    suspend fun pickApkArtifact(owner: String, repo: String, runId: Long): Result<ArtifactInfo> =
        guard {
            withContext(Dispatchers.IO) {
                val artifacts = api().listArtifacts(owner, repo, runId).artifacts
                if (artifacts.isEmpty()) {
                    throw FailureException(
                        AppFailure(
                            appContext.getString(R.string.err_no_artifacts_title),
                            appContext.getString(R.string.err_no_artifacts_hint),
                            appContext.getString(R.string.err_no_artifacts_detail)
                        )
                    )
                }
                val best = artifacts.firstOrNull {
                    it.name.contains("apk", ignoreCase = true)
                } ?: artifacts.first()
                if (best.expired) {
                    throw FailureException(
                        AppFailure(
                            appContext.getString(R.string.err_expired_artifact_title),
                            appContext.getString(R.string.err_expired_artifact_hint),
                            null
                        )
                    )
                }
                best
            }
        }

    suspend fun downloadArtifact(
        owner: String,
        repo: String,
        artifactId: Long,
        destFile: File,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = guard {
        withContext(Dispatchers.IO) {
            destFile.parentFile?.mkdirs()
            val body = api().downloadArtifactZip(owner, repo, artifactId)
            body.use { response ->
                val total = response.contentLength()
                response.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var n: Int
                        var done = 0L
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
            }
            destFile
        }
    }

    companion object {
        private const val MAX_LOG_BYTES = 4 * 1024 * 1024
        private const val MAX_LOG_CHARS = 60_000
    }
}

private fun java.io.InputStream.readBytesCapped(cap: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(32 * 1024)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n == -1) break
        val remaining = cap - total
        if (remaining <= 0) break
        out.write(buf, 0, minOf(n, remaining))
        total += minOf(n, remaining)
    }
    return out.toByteArray()
}
