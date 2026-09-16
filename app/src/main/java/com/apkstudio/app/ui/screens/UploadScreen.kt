package com.apkstudio.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.data.project.PushCollector
import com.apkstudio.app.data.project.WorkflowGenerator
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.FailureException
import kotlinx.coroutines.launch

class UploadViewModel : ViewModel() {
    var started by mutableStateOf(false)
        private set
    var doneCount by mutableStateOf(0)
        private set
    var totalCount by mutableStateOf(1)
        private set
    var currentPath by mutableStateOf("")
        private set
    var failure by mutableStateOf<AppFailure?>(null)
        private set
    var commitSha by mutableStateOf<String?>(null)
        private set

    fun start(onDispatched: () -> Unit) {
        if (started) return
        started = true
        failure = null
        viewModelScope.launch {
            val repo = AppGraph.flow.repo
            val project = AppGraph.flow.project
            if (repo == null || project == null) {
                failure = AppFailure(
                    AppGraph.app.getString(R.string.err_unknown_title),
                    AppGraph.app.getString(R.string.err_unknown_hint)
                )
                return@launch
            }
            val branch = AppGraph.flow.targetBranch
            val collected = PushCollector.collect(project.rootDir)
            totalCount = collected.files.size + 1

            AppGraph.github.pushProject(
                owner = repo.owner.login,
                repo = repo.name,
                branch = branch,
                files = collected.files,
                workflowYaml = WorkflowGenerator.generate(project),
                message = "ApkStudio: upload project for cloud build",
                onProgress = { done, total, path ->
                    doneCount = done
                    totalCount = total
                    currentPath = path
                }
            ).onSuccess { result ->
                commitSha = result.commitSha
                AppGraph.flow.commitSha = result.commitSha
                AppGraph.github.dispatchBuild(repo.owner.login, repo.name, branch)
                    .onSuccess { onDispatched() }
                    .onFailure {
                        failure = (it as? FailureException)?.failure
                    }
            }.onFailure {
                failure = (it as? FailureException)?.failure
            }
        }
    }

    fun retry(onDispatched: () -> Unit) {
        started = false
        doneCount = 0
        currentPath = ""
        start(onDispatched)
    }
}

@Composable
fun UploadScreen(onDispatched: () -> Unit, onBack: () -> Unit) {
    val vm: UploadViewModel = viewModel()

    LaunchedEffect(Unit) {
        vm.start(onDispatched)
    }

    ScreenScaffold(title = stringResource(R.string.upload_title), onBack = onBack) {
        Text(
            text = stringResource(R.string.upload_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        if (vm.failure == null) {
            val progress = if (vm.totalCount > 0) {
                vm.doneCount.toFloat() / vm.totalCount.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${vm.doneCount} / ${vm.totalCount}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (vm.currentPath.isNotEmpty()) {
                    stringResource(R.string.upload_progress_file, vm.currentPath)
                } else "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            vm.commitSha?.let { sha ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.upload_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.commit_label, sha.take(7)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            ErrorCard(
                failure = vm.failure!!,
                onRetry = { vm.retry(onDispatched) }
            )
        }
    }
}
