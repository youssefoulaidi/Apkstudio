package com.apkstudio.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkstudio.app.AppGraph
import com.apkstudio.app.R
import com.apkstudio.app.ui.components.ErrorCard
import com.apkstudio.app.ui.components.ScreenScaffold
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.ErrorMapper
import com.apkstudio.app.util.FailureException
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    var tokenText by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var fieldError by mutableStateOf<String?>(null)
        private set
    var failure by mutableStateOf<AppFailure?>(null)
        private set

    fun clearFailure() {
        failure = null
    }

    fun login(context: Context, onDone: () -> Unit) {
        if (loading) return
        if (tokenText.isBlank()) {
            fieldError = context.getString(R.string.pat_error_empty)
            return
        }
        loading = true
        failure = null
        fieldError = null
        viewModelScope.launch {
            AppGraph.github.validateToken(tokenText)
                .onSuccess { user ->
                    AppGraph.session.saveSession(tokenText, user.login)
                    loading = false
                    onDone()
                }
                .onFailure {
                    loading = false
                    failure = (it as? FailureException)?.failure
                        ?: ErrorMapper.fromThrowable(context, it)
                }
        }
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val vm: LoginViewModel = viewModel()
    val context = LocalContext.current

    ScreenScaffold(title = stringResource(R.string.login_title)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.login_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = vm.tokenText,
                onValueChange = { vm.tokenText = it.trim(); vm.clearFailure() },
                label = { Text(stringResource(R.string.pat_label)) },
                placeholder = { Text(stringResource(R.string.pat_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                isError = vm.fieldError != null,
                supportingText = { vm.fieldError?.let { Text(it) } },
                trailingIcon = {
                    TextButton(onClick = {
                        pasteFromClipboard(context)?.let {
                            vm.tokenText = it.trim()
                        }
                    }) {
                        Text(stringResource(R.string.btn_paste))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { vm.login(context, onDone) },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.btn_login))
                }
            }

            vm.failure?.let { f ->
                Spacer(Modifier.height(12.dp))
                ErrorCard(failure = f, onRetry = { vm.login(context, onDone) })
            }

            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.login_help_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    )
                    HelpStep(stringResource(R.string.login_step_1))
                    HelpStep(stringResource(R.string.login_step_2))
                    HelpStep(stringResource(R.string.login_step_3))
                    HelpStep(stringResource(R.string.login_step_4))
                    Text(
                        text = stringResource(R.string.scopes_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(TOKEN_URL)
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Text(stringResource(R.string.btn_open_token_page))
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpStep(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
    )
}

private fun pasteFromClipboard(context: Context): String? {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    } catch (_: Exception) {
        null
    }
}

private const val TOKEN_URL =
    "https://github.com/settings/tokens/new?scopes=repo,workflow&description=ApkStudio"
