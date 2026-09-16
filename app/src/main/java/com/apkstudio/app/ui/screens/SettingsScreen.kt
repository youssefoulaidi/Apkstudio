package com.apkstudio.app.ui.screens

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.ui.components.SectionTitle
import java.io.File

@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    var lang by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    var showLogout by remember { mutableStateOf(false) }
    var cacheMsg by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.account_section))
            Text(
                text = stringResource(
                    R.string.logged_as,
                    AppGraph.session.login() ?: "?"
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showLogout = true }) {
                Text(stringResource(R.string.btn_logout))
            }

            SectionTitle(stringResource(R.string.lang_section))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = lang.startsWith("ar"),
                    onClick = {
                        lang = "ar"
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("ar")
                        )
                    },
                    label = { Text(stringResource(R.string.lang_ar)) }
                )
                FilterChip(
                    selected = lang.startsWith("en"),
                    onClick = {
                        lang = "en"
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("en")
                        )
                    },
                    label = { Text(stringResource(R.string.lang_en)) }
                )
            }

            SectionTitle(stringResource(R.string.btn_clear_cache))
            OutlinedButton(
                onClick = {
                    try {
                        File(context.cacheDir, "apkstudio-import").deleteRecursively()
                    } catch (_: Exception) {
                    }
                    cacheMsg = context.getString(R.string.cache_cleared)
                }
            ) {
                Text(stringResource(R.string.btn_clear_cache))
            }
            cacheMsg?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SectionTitle(stringResource(R.string.about_section))
            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.version_label, appVersionName(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.confirm_logout_title)) },
            text = { Text(stringResource(R.string.confirm_logout_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    AppGraph.session.clear()
                    AppGraph.flow.resetAll()
                    onLoggedOut()
                }) {
                    Text(stringResource(R.string.confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) {
                    Text(stringResource(R.string.confirm_no))
                }
            }
        )
    }
}

private fun appVersionName(context: Context): String {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }
}
