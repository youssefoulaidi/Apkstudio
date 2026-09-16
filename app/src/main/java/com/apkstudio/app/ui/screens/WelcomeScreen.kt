package com.apkstudio.app.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.LoadingRow
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.FailureException
import kotlinx.coroutines.launch

class WelcomeViewModel : ViewModel() {
    var checking by mutableStateOf(false)
        private set
    var failure by mutableStateOf<AppFailure?>(null)
        private set

    fun check(onLinked: () -> Unit, onLoginNeeded: () -> Unit) {
        if (checking) return
        val token = AppGraph.session.token()
        if (token == null) {
            onLoginNeeded()
            return
        }
        checking = true
        failure = null
        viewModelScope.launch {
            AppGraph.github.validateToken(token)
                .onSuccess {
                    checking = false
                    onLinked()
                }
                .onFailure {
                    checking = false
                    failure = (it as? FailureException)?.failure
                }
        }
    }
}

@Composable
fun WelcomeScreen(onLinked: () -> Unit, onLoginNeeded: () -> Unit) {
    val vm: WelcomeViewModel = viewModel()
    var lang by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.feat_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                FeatureRow(stringResource(R.string.feat_1))
                FeatureRow(stringResource(R.string.feat_2))
                FeatureRow(stringResource(R.string.feat_3))
                FeatureRow(stringResource(R.string.feat_4))
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(text = stringResource(R.string.lang_title), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = lang.startsWith("ar"),
                onClick = {
                    lang = "ar"
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                },
                label = { Text(stringResource(R.string.lang_ar)) }
            )
            FilterChip(
                selected = lang.startsWith("en"),
                onClick = {
                    lang = "en"
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                },
                label = { Text(stringResource(R.string.lang_en)) }
            )
        }
        Spacer(Modifier.height(24.dp))

        if (vm.checking) {
            LoadingRow(stringResource(R.string.welcome_checking))
        } else {
            Button(
                onClick = { vm.check(onLinked, onLoginNeeded) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_start))
            }
        }

        vm.failure?.let { f ->
            Spacer(Modifier.height(16.dp))
            ErrorCard(failure = f, onRetry = { vm.check(onLinked, onLoginNeeded) })
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoginNeeded) {
                Text(stringResource(R.string.btn_login))
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "✓ ",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
