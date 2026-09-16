package com.apkstudio.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.ui.components.InfoRow
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.ui.components.SectionTitle
import com.apkstudio.app.util.ZipUtils

@Composable
fun AnalysisScreen(onUpload: () -> Unit, onBack: () -> Unit) {
    val project = AppGraph.flow.project
    val repo = AppGraph.flow.repo

    ScreenScaffold(title = stringResource(R.string.analysis_title), onBack = onBack) {
        if (project == null) {
            Text(
                text = stringResource(R.string.err_unknown_title),
                style = MaterialTheme.typography.bodyLarge
            )
            return@ScreenScaffold
        }
        val unknown = stringResource(R.string.unknown_val)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (repo != null) {
                Text(
                    text = stringResource(R.string.selected_repo, repo.fullName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(stringResource(R.string.row_package), project.packageId ?: unknown)
                    InfoRow(stringResource(R.string.row_module), project.module)
                    InfoRow(
                        stringResource(R.string.row_compile_sdk),
                        project.compileSdk?.toString() ?: unknown
                    )
                    InfoRow(
                        stringResource(R.string.row_min_sdk),
                        project.minSdk?.toString() ?: unknown
                    )
                    InfoRow(
                        stringResource(R.string.row_target_sdk),
                        project.targetSdk?.toString() ?: unknown
                    )
                    InfoRow(stringResource(R.string.row_agp), project.agpVersion ?: unknown)
                    InfoRow(stringResource(R.string.row_gradle), project.gradleVersion ?: unknown)
                    InfoRow(stringResource(R.string.row_kotlin), project.kotlinVersion ?: unknown)
                    InfoRow(
                        stringResource(R.string.row_jdk),
                        project.recommendedJdk.toString()
                    )
                    InfoRow(stringResource(R.string.row_files), project.fileCount.toString())
                    InfoRow(
                        stringResource(R.string.row_size),
                        ZipUtils.formatBytes(project.totalBytes)
                    )
                }
            }

            if (project.warnings.isNotEmpty()) {
                SectionTitle(stringResource(R.string.warn_title, project.warnings.size))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        project.warnings.forEach { w ->
                            Text(
                                text = "⚠ $w",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "✓ " + stringResource(R.string.no_warnings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!project.isValid) {
                SectionTitle(stringResource(R.string.err_title_list, project.errors.size))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        project.errors.forEach { e ->
                            Text(
                                text = "✗ $e",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onUpload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.btn_upload_build),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
