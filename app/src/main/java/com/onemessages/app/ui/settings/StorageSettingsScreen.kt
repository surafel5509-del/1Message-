@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.ui.components.SettingsRow
import com.onemessages.app.ui.components.SettingsSection

@Composable
fun StorageSettingsRoute(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val environment by viewModel.environment.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_storage)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
            SettingsSection(stringResource(R.string.settings_storage)) {
                SettingsRow(
                    title = stringResource(R.string.storage_database),
                    trailingText = viewModel.format(usage.databaseBytes),
                )
                SettingsRow(
                    title = stringResource(R.string.storage_preferences),
                    trailingText = viewModel.format(usage.preferencesBytes),
                )
                SettingsRow(
                    title = stringResource(R.string.storage_attachments),
                    trailingText = viewModel.format(usage.cacheBytes),
                )
                SettingsRow(
                    title = stringResource(R.string.storage_total),
                    trailingText = viewModel.format(usage.totalBytes),
                )
                TextButton(onClick = viewModel::clearCache, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.storage_clear_cache))
                }
            }

            SettingsSection(stringResource(R.string.diagnostics_sms_role)) {
                SettingsRow(
                    title = stringResource(R.string.diagnostics_default_sms),
                    trailingText = if (environment?.isDefaultSmsApp == true) "✓" else "—",
                )
                SettingsRow(
                    title = stringResource(R.string.diagnostics_sims),
                    trailingText = environment?.simCount?.toString() ?: "—",
                )
                SettingsRow(
                    title = stringResource(R.string.diagnostics_radio),
                    trailingText = environment?.radioState ?: "—",
                )
            }

            SettingsSection(stringResource(R.string.diagnostics)) {
                Text(
                    text = stringResource(R.string.diagnostics_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diagnostics_empty),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        entries.take(30).forEach { entry ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    text = entry.level,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    text = "${entry.tag}: ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.padding(8.dp)) {
                    TextButton(onClick = {
                        viewModel.exportDiagnostics { text ->
                            clipboard.setText(AnnotatedString(text))
                        }
                    }) { Text(stringResource(R.string.diagnostics_export)) }
                    TextButton(onClick = viewModel::clearDiagnostics) {
                        Text(stringResource(R.string.diagnostics_clear))
                    }
                }
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.refresh_short))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
