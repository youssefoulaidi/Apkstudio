package com.apkstudio.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.data.project.ProjectAnalyzer
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.LoadingRow
import com.apkstudio.app.ui.components.PillKind
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.ui.components.StatusPill
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.ErrorMapper
import com.apkstudio.app.util.FailureException
import com.apkstudio.app.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImportViewModel : ViewModel() {
    var analyzing by mutableStateOf(false)
        private set
    var fileName by mutableStateOf<String?>(null)
        private set
    var failure by mutableStateOf<AppFailure?>(null)

    fun importZip(context: Context, uri: Uri, onAnalyzed: () -> Unit) {
        if (analyzing) return
        analyzing = true
        failure = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = queryDisplayName(context, uri) ?: "project.zip"
                withContext(Dispatchers.Main) { fileName = name }
                val dest = File(
                    context.cacheDir,
                    "apkstudio-import/input-${System.currentTimeMillis()}.zip"
                )
                ZipUtils.copyUriToFile(context, uri, dest)
                val result = ProjectAnalyzer.analyze(context, dest)
                withContext(Dispatchers.Main) {
                    analyzing = false
                    result
                        .onSuccess {
                            AppGraph.flow.project = it
                            AppGraph.flow.commitSha = null
                            AppGraph.flow.run = null
                            onAnalyzed()
                        }
                        .onFailure {
                            failure = (it as? FailureException)?.failure
                                ?: ErrorMapper.fromThrowable(context, it)
                        }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    analyzing = false
                    failure = ErrorMapper.fromThrowable(context, t)
                }
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun ImportScreen(onAnalyzed: () -> Unit, onBack: () -> Unit) {
    val vm: ImportViewModel = viewModel()
    val context = LocalContext.current
    val repo = AppGraph.flow.repo

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importZip(context, uri, onAnalyzed)
    }

    ScreenScaffold(title = stringResource(R.string.import_title), onBack = onBack) {
        if (repo != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selected_repo, repo.fullName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Row {
                        StatusPill(
                            text = stringResource(
                                if (repo.private) R.string.vis_private else R.string.vis_public
                            ),
                            kind = if (repo.private) PillKind.Warning else PillKind.Success
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (repo.private) R.string.minutes_private
                            else R.string.minutes_public
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.import_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pick_zip_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        if (vm.analyzing) {
            LoadingRow(
                stringResource(R.string.analyzing_title) +
                    (vm.fileName?.let { " — $it" } ?: "")
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.analyzing_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_pick_zip))
            }
        }

        vm.failure?.let { f ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(failure = f, onRetry = { picker.launch(arrayOf("*/*")) })
        }
    }
}
