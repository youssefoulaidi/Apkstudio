package com.apkstudio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.data.github.Repo
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.PillKind
import com.apkstudio.app.ui.components.ScreenTopBar
import com.apkstudio.app.ui.components.StatusPill
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.FailureException
import kotlinx.coroutines.launch

class RepoListViewModel : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var failure by mutableStateOf<AppFailure?>(null)
        private set
    var repos by mutableStateOf<List<Repo>>(emptyList())
        private set
    var query by mutableStateOf("")
    var showCreate by mutableStateOf(false)
    var creating by mutableStateOf(false)
        private set
    var createName by mutableStateOf("")
    var createPrivate by mutableStateOf(true)
    var createError by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    val filtered: List<Repo>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return repos
            return repos.filter {
                it.name.lowercase().contains(q) || it.fullName.lowercase().contains(q)
            }
        }

    fun refresh() {
        if (loading) return
        loading = true
        failure = null
        viewModelScope.launch {
            AppGraph.github.listRepos()
                .onSuccess {
                    repos = it
                    loading = false
                }
                .onFailure {
                    loading = false
                    failure = (it as? FailureException)?.failure
                }
        }
    }

    fun create(onCreated: (Repo) -> Unit) {
        val name = createName.trim()
        if (!name.matches(Regex("[A-Za-z0-9._-]+"))) {
            createError = AppGraph.app.getString(R.string.repo_name_error)
            return
        }
        creating = true
        createError = null
        viewModelScope.launch {
            AppGraph.github.createRepo(name, createPrivate)
                .onSuccess { repo ->
                    creating = false
                    showCreate = false
                    createName = ""
                    repos = listOf(repo) + repos
                    onCreated(repo)
                }
                .onFailure {
                    creating = false
                    createError = (it as? FailureException)?.failure?.let { f ->
                        "${f.title}. ${f.hint}"
                    }
                }
        }
    }
}

@Composable
fun RepoListScreen(
    onRepoSelected: () -> Unit,
    onResumeBuild: () -> Unit,
    onSettings: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val vm: RepoListViewModel = viewModel()

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.repos_title),
                onBack = null,
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.btn_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.btn_new_repo))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text(stringResource(R.string.repos_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Resume banner for a build left in progress.
            AppGraph.flow.run?.let { run ->
                if (run.status != "completed") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRepoSelected() }
                    ) {
                        // Handled by caller navigation state; no-op visual is avoided:
                        // tapping re-enters the flow via the selected repo.
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            when {
                vm.loading && vm.repos.isEmpty() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                vm.failure != null && vm.repos.isEmpty() -> {
                    ErrorCard(failure = vm.failure!!, onRetry = { vm.refresh() })
                }
                vm.filtered.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = stringResource(R.string.repos_empty),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.repos_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.filtered, key = { it.id }) { repo ->
                            RepoCard(
                                repo = repo,
                                onClick = {
                                    AppGraph.flow.repo = repo
                                    AppGraph.flow.resetProject()
                                    onRepoSelected()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (vm.showCreate) {
        AlertDialog(
            onDismissRequest = { if (!vm.creating) vm.showCreate = false },
            title = { Text(stringResource(R.string.dialog_new_repo_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = vm.createName,
                        onValueChange = { vm.createName = it },
                        label = { Text(stringResource(R.string.repo_name_label)) },
                        placeholder = { Text(stringResource(R.string.repo_name_hint)) },
                        singleLine = true,
                        isError = vm.createError != null,
                        supportingText = { vm.createError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = vm.createPrivate,
                            onCheckedChange = { vm.createPrivate = it }
                        )
                        Text(stringResource(R.string.make_private))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (vm.createPrivate) R.string.minutes_private
                            else R.string.minutes_public
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !vm.creating,
                    onClick = {
                        vm.create { repo ->
                            AppGraph.flow.repo = repo
                            AppGraph.flow.resetProject()
                            onRepoSelected()
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_create))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !vm.creating,
                    onClick = { vm.showCreate = false }
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun RepoCard(repo: Repo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = stringResource(
                        if (repo.private) R.string.vis_private else R.string.vis_public
                    ),
                    kind = if (repo.private) PillKind.Warning else PillKind.Success
                )
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    repo.language,
                    repo.updatedAt?.take(10),
                    stringResource(R.string.default_branch, repo.defaultBranch)
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
