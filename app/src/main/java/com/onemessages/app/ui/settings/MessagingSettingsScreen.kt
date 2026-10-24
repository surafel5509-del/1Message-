@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.ui.components.SettingsRow
import com.onemessages.app.ui.components.SettingsSection
import com.onemessages.app.ui.components.SettingsSwitchRow

@Composable
fun MessagingSettingsRoute(
    onBack: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val device by viewModel.deviceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var signature by remember(settings.signature) { mutableStateOf(settings.signature) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sim_settings_title)) },
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
            SettingsSection(stringResource(R.string.sim_settings_default)) {
                if (device.sims.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sim_none_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    TextButton(onClick = onRequestPhonePermission, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.onboarding_permission_phone_body))
                    }
                } else {
                    SettingsRow(
                        title = stringResource(R.string.sim_settings_ask),
                        subtitle = stringResource(R.string.sim_settings_default_body),
                        trailingText = if (settings.preferredSubscriptionId < 0) "✓" else null,
                        onClick = { viewModel.setPreferredSubscription(-1) },
                    )
                    device.sims.forEach { sim ->
                        SettingsRow(
                            title = stringResource(R.string.sim_number, sim.slotIndex + 1, sim.label),
                            subtitle = sim.carrierName,
                            trailingText = if (settings.preferredSubscriptionId == sim.subscriptionId) "✓" else null,
                            onClick = { viewModel.setPreferredSubscription(sim.subscriptionId) },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_sim)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.sim_settings_delivery_reports),
                    subtitle = stringResource(R.string.sim_settings_delivery_reports_body),
                    checked = settings.requestDeliveryReports,
                    onCheckedChange = viewModel::setDeliveryReports,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.sim_settings_retry),
                    subtitle = stringResource(R.string.sim_settings_retry_body),
                    checked = settings.autoRetryFailedSends,
                    onCheckedChange = viewModel::setAutoRetry,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.sim_settings_auto_download),
                    subtitle = stringResource(R.string.sim_settings_auto_download_body),
                    checked = settings.autoDownloadMms,
                    onCheckedChange = viewModel::setAutoDownloadMms,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.sim_settings_group_mms),
                    subtitle = stringResource(R.string.sim_settings_group_mms_body),
                    checked = settings.groupMessagingAsMms,
                    onCheckedChange = viewModel::setGroupAsMms,
                )
                SettingsRow(
                    title = stringResource(R.string.sim_settings_multipart),
                    subtitle = stringResource(R.string.sim_settings_multipart_body),
                )
            }

            SettingsSection(stringResource(R.string.sim_settings_mms)) {
                Text(
                    text = stringResource(R.string.mms_not_supported_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            SettingsSection(stringResource(R.string.sim_settings_signature)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = signature,
                        onValueChange = {
                            signature = it.take(120)
                            viewModel.setSignature(signature)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sim_settings_signature_body)) },
                        maxLines = 2,
                    )
                }
            }

            SettingsSection(stringResource(R.string.sim_settings_manage_sims)) {
                SettingsRow(
                    title = stringResource(R.string.sim_settings_open_system),
                    subtitle = stringResource(R.string.sim_settings_manage_sims_body),
                    showChevron = true,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            })
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
