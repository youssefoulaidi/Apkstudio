package com.apkstudio.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.data.github.ArtifactInfo
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.LoadingRow
import com.apkstudio.app.ui.components.PillKind
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.ui.components.SectionTitle
import com.apkstudio.app.ui.components.StatusPill
import com.apkstudio.app.util.ApkInstaller
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.FailureException
import com.apkstudio.app.util.InstallAction
import com.apkstudio.app.util.ZipUtils
import kotlinx.coroutines.launch
import java.io.File

class ResultViewModel : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var failure by mutableStateOf<AppFailure?>(null)
    var success by mutableStateOf<Boolean?>(null)
        private set
    var artifact by mutableStateOf<ArtifactInfo?>(null)
        private set
    var failedSteps by mutableStateOf<List<String>>(emptyList())
        private set
    var logExcerpt by mutableStateOf<String?>(null)
        private set
    var downloading by mutableStateOf(false)
        private set
    var dlDone by mutableStateOf(0L)
        private set
    var dlTotal by mutableStateOf(0L)
        private set
    var apkPath by mutableStateOf<String?>(null)
        private set
    var savedDownloads by mutableStateOf(false)
        private set
    var rebuilding by mutableStateOf(false)
        private set
    var showUnknownSources by mutableStateOf(false)
    private var apkFile: File? = null
    private var loaded = false
    private var pendingSettingsIntent: Intent? = null

    fun load() {
        if (loaded) return
        loaded = true
        val repo = AppGraph.flow.repo
        val run = AppGraph.flow.run
        if (repo == null || run == null) {
            failure = AppFailure(
                AppGraph.app.getString(R.string.err_unknown_title),
                AppGraph.app.getString(R.string.err_unknown_hint)
            )
            return
        }
        success = run.conclusion == "success"
        loading = true
        viewModelScope.launch {
            AppGraph.github.getJobs(repo.owner.login, repo.name, run.id)
                .onSuccess { jobs ->
                    failedSteps = jobs.flatMap { job ->
                        job.steps
                            .filter { it.conclusion == "failure" }
                            .map { "${job.name}: ${it.name}" }
                    }
                }
            AppGraph.github.getLogs(repo.owner.login, repo.name, run.id)
                .onSuccess { logExcerpt = extractExcerpt(it) }
                .onFailure { logExcerpt = null }
            if (success == true) {
                AppGraph.github.pickApkArtifact(repo.owner.login, repo.name, run.id)
                    .onSuccess { artifact = it }
                    .onFailure {
                        failure = (it as? FailureException)?.failure
                    }
            }
            loading = false
        }
    }

    fun retryLoad() {
        loaded = false
        failure = null
        load()
    }

    fun download(context: Context) {
        val repo = AppGraph.flow.repo
        val run = AppGraph.flow.run
        val art = artifact
        if (repo == null || run == null || art == null || downloading) return
        downloading = true
        failure = null
        dlDone = 0
        dlTotal = 0
        viewModelScope.launch {
            val outDir = File(context.filesDir, "apkstudio/output")
            val zipDest = File(outDir, "run-${run.runNumber}.zip")
            AppGraph.github.downloadArtifact(
                repo.owner.login, repo.name, art.id, zipDest
            ) { done, total ->
                dlDone = done
                dlTotal = total
            }.onSuccess { zip ->
                try {
                    val extractDir = File(outDir, "run-${run.runNumber}")
                    ZipUtils.unzip(zip, extractDir)
                    val apks = extractDir.walkTopDown()
                        .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                        .toList()
                    if (apks.isEmpty()) {
                        failure = AppFailure(
                            context.getString(R.string.err_no_apk_in_zip_title),
                            context.getString(R.string.err_no_apk_in_zip_hint),
                            context.getString(R.string.err_no_apk_in_zip_detail)
                        )
                    } else {
                        val best = apks.firstOrNull {
                            it.name.contains("debug", ignoreCase = true)
                        } ?: apks.maxByOrNull { it.length() }!!
                        val finalName = "${repo.name}-debug-${run.runNumber}.apk"
                        val finalApk = File(outDir, finalName)
                        best.copyTo(finalApk, overwrite = true)
                        apkFile = finalApk
                        apkPath = finalApk.absolutePath
                        savedDownloads =
                            ApkInstaller.copyToDownloads(context, finalApk, finalName) != null
                    }
                } catch (e: Exception) {
                    failure = AppFailure(
                        context.getString(R.string.err_download_title),
                        context.getString(R.string.err_download_hint),
                        e.message?.take(300)
                    )
                }
                downloading = false
            }.onFailure {
                downloading = false
                failure = (it as? FailureException)?.failure
            }
        }
    }

    fun install(context: Context) {
        val apk = apkFile ?: return
        when (val action = ApkInstaller.installIntent(context, apk)) {
            is InstallAction.Install -> {
                try {
                    action.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(action.intent)
                } catch (_: Exception) {
                }
            }
            is InstallAction.NeedUnknownSources -> {
                pendingSettingsIntent = action.settingsIntent
                showUnknownSources = true
            }
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        showUnknownSources = false
        pendingSettingsIntent?.let {
            try {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            } catch (_: Exception) {
            }
        }
    }

    fun rebuild(onRebuild: () -> Unit) {
        val repo = AppGraph.flow.repo
        if (repo == null || rebuilding) return
        rebuilding = true
        failure = null
        viewModelScope.launch {
            val branch = AppGraph.flow.targetBranch
            AppGraph.github.dispatchBuild(repo.owner.login, repo.name, branch)
                .onSuccess {
                    val sha = AppGraph.flow.commitSha
                    if (sha == null) {
                        rebuilding = false
                        return@launch
                    }
                    AppGraph.github.findRunBySha(repo.owner.login, repo.name, sha)
                        .onSuccess {
                            AppGraph.flow.run = it
                            rebuilding = false
                            onRebuild()
                        }
                        .onFailure {
                            rebuilding = false
                            failure = (it as? FailureException)?.failure
                        }
                }
                .onFailure {
                    rebuilding = false
                    failure = (it as? FailureException)?.failure
                }
        }
    }

    private fun extractExcerpt(text: String): String? {
        if (text.isBlank()) return null
        val lines = text.lines()
        val pattern = Regex("(?i)\\b(error|failed|failure|exception)\\b|^e: |FAILED|What went wrong|Execution failed")
        val hits = lines.filter { pattern.containsMatchIn(it) }.takeLast(80)
        return if (hits.isNotEmpty()) {
            hits.joinToString("\n")
        } else {
            lines.takeLast(40).joinToString("\n")
        }
    }
}

@Composable
fun ResultScreen(onNewBuild: () -> Unit, onRebuild: () -> Unit) {
    val vm: ResultViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load()
    }

    ScreenScaffold(title = stringResource(R.string.build_title)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (vm.loading) {
                LoadingRow(stringResource(R.string.logs_loading))
                return@Column
            }

            when (vm.success) {
                true -> SuccessBody(vm = vm, context = context, onNewBuild = onNewBuild)
                false -> FailBody(vm = vm, context = context, onNewBuild = onNewBuild, onRebuild = onRebuild)
                null -> vm.failure?.let { f ->
                    ErrorCard(failure = f, onRetry = { vm.retryLoad() })
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onNewBuild) {
                        Text(stringResource(R.string.btn_new_project))
                    }
                }
            }
        }
    }

    if (vm.showUnknownSources) {
        AlertDialog(
            onDismissRequest = { vm.showUnknownSources = false },
            title = { Text(stringResource(R.string.unknown_sources_title)) },
            text = { Text(stringResource(R.string.unknown_sources_msg)) },
            confirmButton = {
                TextButton(onClick = { vm.openUnknownSourcesSettings(context) }) {
                    Text(stringResource(R.string.btn_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showUnknownSources = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun SuccessBody(vm: ResultViewModel, context: Context, onNewBuild: () -> Unit) {
    StatusPill(text = stringResource(R.string.concl_success), kind = PillKind.Success)
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.result_success_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))

    vm.artifact?.let { art ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${stringResource(R.string.artifact_label)}: ${art.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.size_label, ZipUtils.formatBytes(art.sizeInBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    vm.failure?.let { f ->
        ErrorCard(
            failure = f,
            onRetry = {
                if (vm.artifact == null) vm.retryLoad() else vm.download(context)
            }
        )
        Spacer(Modifier.height(12.dp))
    }

    when {
        vm.downloading -> {
            if (vm.dlTotal > 0) {
                LinearProgressIndicator(
                    progress = vm.dlDone.toFloat() / vm.dlTotal.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${ZipUtils.formatBytes(vm.dlDone)} / " +
                    if (vm.dlTotal > 0) ZipUtils.formatBytes(vm.dlTotal) else "…",
                style = MaterialTheme.typography.bodySmall
            )
        }
        vm.apkPath == null && vm.artifact != null -> {
            Button(
                onClick = { vm.download(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_download))
            }
        }
        vm.apkPath != null -> {
            Text(
                text = stringResource(R.string.downloaded_path, vm.apkPath!!),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            if (vm.savedDownloads) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.saved_downloads),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.install(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_install))
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onNewBuild) {
            Text(stringResource(R.string.btn_new_project))
        }
        AppGraph.flow.run?.let { r ->
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.htmlUrl)))
                    } catch (_: Exception) {
                    }
                }
            ) {
                Text(stringResource(R.string.btn_view_on_github))
            }
        }
    }
}

@Composable
private fun FailBody(
    vm: ResultViewModel,
    context: Context,
    onNewBuild: () -> Unit,
    onRebuild: () -> Unit
) {
    StatusPill(text = stringResource(R.string.concl_failure), kind = PillKind.Error)
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.result_fail_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))

    if (vm.failedSteps.isNotEmpty()) {
        SectionTitle(stringResource(R.string.failed_steps_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                vm.failedSteps.forEach { s ->
                    Text(
                        text = "✗ $s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    vm.logExcerpt?.let { excerpt ->
        SectionTitle(stringResource(R.string.build_log_excerpt))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = excerpt,
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    vm.failure?.let { f ->
        ErrorCard(failure = f)
        Spacer(Modifier.height(12.dp))
    }

    if (vm.rebuilding) {
        LoadingRow(stringResource(R.string.finding_run))
    } else {
        Button(
            onClick = { vm.rebuild(onRebuild) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_rebuild))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onNewBuild) {
            Text(stringResource(R.string.btn_new_project))
        }
        AppGraph.flow.run?.let { r ->
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.htmlUrl)))
                    } catch (_: Exception) {
                    }
                }
            ) {
                Text(stringResource(R.string.btn_view_on_github))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (vm.rebuilding) {
        CircularProgressIndicator()
    }
}
