@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.ui.components.SettingsSection

@Composable
fun BackupSettingsRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(state.messageRes) {
        state.messageRes?.let { res ->
            val text = state.messageArg?.let { context.getString(res, it) } ?: context.getString(res)
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_backup)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(stringResource(R.string.backup_export)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backup_export_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.backup_export_password_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = { Text(stringResource(R.string.backup_export_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::setConfirmPassword,
                        label = { Text(stringResource(R.string.save)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            exportLauncher.launch("1messages-backup-${System.currentTimeMillis()}.1mbak")
                        },
                        enabled = !state.busy,
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.backup_export_action))
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.backup_import)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backup_import_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/*")) },
                        enabled = !state.busy && state.password.length >= 8,
                    ) {
                        Text(stringResource(R.string.backup_import_action))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backup_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
