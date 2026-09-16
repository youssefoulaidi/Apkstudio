package com.apkstudio.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.apkstudio.app.data.github.JobInfo
import com.apkstudio.app.data.github.WorkflowRun
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.LoadingRow
import com.apkstudio.app.ui.components.PillKind
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.ui.components.SectionTitle
import com.apkstudio.app.ui.components.StatusPill
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.BuildNotifications
import com.apkstudio.app.util.FailureException
import com.apkstudio.app.util.ZipUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BuildViewModel : ViewModel() {
    var phase by mutableStateOf("idle") // idle | finding | monitoring | failed
        private set
    var run by mutableStateOf<WorkflowRun?>(null)
        private set
    var jobs by mutableStateOf<List<JobInfo>>(emptyList())
        private set
    var failure by mutableStateOf<AppFailure?>(null)
        private set
    var logs by mutableStateOf<String?>(null)
        private set
    var logsLoading by mutableStateOf(false)
        private set
    var showLogs by mutableStateOf(false)
    var startTime = SystemClock.elapsedRealtime()
        private set
    private var started = false

    fun start(onFinished: () -> Unit) {
        if (started) return
        started = true
        startTime = SystemClock.elapsedRealtime()
        val repo = AppGraph.flow.repo
        if (repo == null) {
            failure = AppFailure(
                AppGraph.app.getString(R.string.err_unknown_title),
                AppGraph.app.getString(R.string.err_unknown_hint)
            )
            phase = "failed"
            return
        }
        viewModelScope.launch {
            val existing = AppGraph.flow.run
            if (existing != null && existing.status == "completed") {
                onFinished()
                return@launch
            }
            var current = existing
            if (current == null) {
                phase = "finding"
                val sha = AppGraph.flow.commitSha
                if (sha == null) {
                    failure = AppFailure(
                        AppGraph.app.getString(R.string.err_run_not_found_title),
                        AppGraph.app.getString(R.string.err_run_not_found_hint)
                    )
                    phase = "failed"
                    return@launch
                }
                var found: WorkflowRun? = null
                AppGraph.github.findRunBySha(repo.owner.login, repo.name, sha)
                    .onSuccess { found = it }
                    .onFailure {
                        failure = (it as? FailureException)?.failure
                        phase = "failed"
                        return@launch
                    }
                current = found!!
                run = current
                AppGraph.flow.run = current
            } else {
                run = current
            }

            phase = "monitoring"
            var errors = 0
            val runId = current!!.id
            while (true) {
                var ok = true
                AppGraph.github.getRun(repo.owner.login, repo.name, runId)
                    .onSuccess {
                        run = it
                        AppGraph.flow.run = it
                    }
                    .onFailure { ok = false }
                AppGraph.github.getJobs(repo.owner.login, repo.name, runId)
                    .onSuccess { jobs = it }
                    .onFailure { ok = false }
                if (!ok) {
                    errors++
                    if (errors >= 5) {
                        failure = AppFailure(
                            AppGraph.app.getString(R.string.err_network_title),
                            AppGraph.app.getString(R.string.err_network_hint)
                        )
                        phase = "failed"
                        return@launch
                    }
                } else {
                    errors = 0
                }
                if (run?.status == "completed") break
                delay(12_000)
            }
            AppGraph.github.getJobs(repo.owner.login, repo.name, runId)
                .onSuccess { jobs = it }
            BuildNotifications.notifyFinished(AppGraph.app, run?.conclusion == "success")
            onFinished()
        }
    }

    fun retryFind(onFinished: () -> Unit) {
        started = false
        failure = null
        phase = "idle"
        start(onFinished)
    }

    fun toggleLogs() {
        showLogs = !showLogs
        if (showLogs && logs == null && !logsLoading) {
            val repo = AppGraph.flow.repo
            val id = run?.id ?: return
            logsLoading = true
            viewModelScope.launch {
                AppGraph.github.getLogs(repo!!.owner.login, repo.name, id)
                    .onSuccess { logs = it }
                    .onFailure { logs = "" }
                logsLoading = false
            }
        }
    }
}

@Composable
fun BuildScreen(onFinished: () -> Unit, onLeave: () -> Unit) {
    val vm: BuildViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.start(onFinished)
    }

    var elapsed by mutableStateOf(0L)
    LaunchedEffect(vm.phase) {
        while (true) {
            elapsed = (SystemClock.elapsedRealtime() - vm.startTime) / 1000
            delay(1000)
        }
    }

    ScreenScaffold(title = stringResource(R.string.build_title)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            when (vm.phase) {
                "finding" -> LoadingRow(stringResource(R.string.finding_run))
                "failed" -> {
                    vm.failure?.let { f ->
                        ErrorCard(
                            failure = f,
                            onRetry = { vm.retryFind(onFinished) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.run?.let { r ->
                                OutlinedButton(onClick = { openUrl(context, r.htmlUrl) }) {
                                    Text(stringResource(R.string.btn_view_on_github))
                                }
                            }
                            TextButton(onClick = onLeave) {
                                Text(stringResource(R.string.btn_new_project))
                            }
                        }
                    }
                }
                else -> vm.run?.let { r ->
                    RunCard(run = r, elapsed = elapsed)
                }
            }

            if (vm.phase == "monitoring" && vm.run?.status == "queued") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.build_queued_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (vm.jobs.isNotEmpty()) {
                SectionTitle(stringResource(R.string.steps_title))
                vm.jobs.forEach { job ->
                    JobCard(job = job)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (vm.phase == "monitoring") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.toggleLogs() }) {
                    Text(
                        stringResource(
                            if (vm.showLogs) R.string.btn_hide_logs else R.string.btn_show_logs
                        )
                    )
                }
                if (vm.showLogs) {
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        if (vm.logsLoading) {
                            Text(
                                text = stringResource(R.string.logs_loading),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = vm.logs?.ifBlank { null }
                                    ?: context.getString(R.string.logs_empty),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.run?.let { r ->
                        OutlinedButton(onClick = { openUrl(context, r.htmlUrl) }) {
                            Text(stringResource(R.string.btn_view_on_github))
                        }
                    }
                    TextButton(onClick = onLeave) {
                        Text(stringResource(R.string.btn_new_project))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.note_leave_safe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RunCard(run: WorkflowRun, elapsed: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.run_label,
                    run.runNumber,
                    run.headBranch ?: ""
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = statusText(run.status), kind = statusKind(run.status))
                if (run.status == "completed") {
                    StatusPill(
                        text = conclusionText(run.conclusion),
                        kind = conclusionKind(run.conclusion)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.elapsed_label,
                    ZipUtils.formatDuration(elapsed)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun JobCard(job: JobInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = job.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = if (job.status == "completed") {
                        conclusionText(job.conclusion)
                    } else {
                        statusText(job.status)
                    },
                    kind = if (job.status == "completed") {
                        conclusionKind(job.conclusion)
                    } else {
                        statusKind(job.status)
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            job.steps.forEach { step ->
                val mark = when {
                    step.conclusion == "success" -> "✓"
                    step.conclusion == "failure" -> "✗"
                    step.conclusion == "cancelled" -> "○"
                    step.status == "in_progress" -> "…"
                    else -> "·"
                }
                Text(
                    text = "$mark ${step.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.conclusion == "failure") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun statusText(status: String?): String {
    return when (status) {
        "queued" -> stringResource(R.string.st_queued)
        "in_progress" -> stringResource(R.string.st_in_progress)
        "completed" -> stringResource(R.string.st_completed)
        else -> status ?: ""
    }
}

private fun statusKind(status: String?): PillKind {
    return when (status) {
        "completed" -> PillKind.Info
        "in_progress" -> PillKind.Warning
        else -> PillKind.Neutral
    }
}

@Composable
private fun conclusionText(conclusion: String?): String {
    return when (conclusion) {
        "success" -> stringResource(R.string.concl_success)
        "failure" -> stringResource(R.string.concl_failure)
        "cancelled" -> stringResource(R.string.concl_cancelled)
        "timed_out" -> stringResource(R.string.concl_timed_out)
        "action_required" -> stringResource(R.string.concl_action_required)
        else -> stringResource(R.string.concl_unknown)
    }
}

private fun conclusionKind(conclusion: String?): PillKind {
    return when (conclusion) {
        "success" -> PillKind.Success
        "failure", "timed_out" -> PillKind.Error
        else -> PillKind.Neutral
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}
