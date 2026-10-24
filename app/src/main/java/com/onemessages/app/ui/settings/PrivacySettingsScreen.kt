@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.onemessages.app.permissions.AppPermissions
import com.onemessages.app.permissions.PermissionGroup
import com.onemessages.app.ui.components.ConfirmDialog
import com.onemessages.app.ui.components.SettingsRow
import com.onemessages.app.ui.components.SettingsSection
import com.onemessages.app.ui.components.SettingsSwitchRow
import com.onemessages.app.ui.lock.AppLockAuthenticator

@Composable
fun PrivacySettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val blockedSenders by viewModel.blockedSenders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    val canBiometric = remember { AppLockAuthenticator.canAuthenticate(context) }
    val permissions = remember {
        PermissionGroup.entries.associateWith { AppPermissions.isGranted(context, it) }
    }

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
                title = { Text(stringResource(R.string.settings_privacy)) },
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
            SettingsSection(stringResource(R.string.privacy_app_lock)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.privacy_app_lock_enable),
                    subtitle = if (canBiometric) {
                        stringResource(R.string.privacy_app_lock_body)
                    } else {
                        stringResource(R.string.privacy_app_lock_unavailable)
                    },
                    checked = settings.appLockEnabled,
                    enabled = canBiometric,
                    onCheckedChange = viewModel::setAppLock,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_lock_screen),
                    subtitle = stringResource(R.string.privacy_hide_preview_body),
                    checked = settings.hideContentOnLockScreen,
                    onCheckedChange = viewModel::setHideOnLock,
                )
            }

            SettingsSection(stringResource(R.string.privacy_otp_detection)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.privacy_otp_detection),
                    subtitle = stringResource(R.string.privacy_otp_detection_body),
                    checked = settings.otpDetectionEnabled,
                    onCheckedChange = viewModel::setOtpDetection,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.privacy_hide_otp),
                    subtitle = stringResource(R.string.privacy_hide_otp_body),
                    checked = settings.hideOtpDigits,
                    onCheckedChange = viewModel::setHideOtpDigits,
                    enabled = settings.otpDetectionEnabled,
                )
            }

            SettingsSection(stringResource(R.string.blocked_senders)) {
                if (blockedSenders.isEmpty()) {
                    SettingsRow(
                        title = stringResource(R.string.blocked_senders_empty),
                        subtitle = stringResource(R.string.per_conversation_block_body),
                    )
                } else {
                    blockedSenders.forEach { conversation ->
                        SettingsRow(
                            title = conversation.displayName.ifBlank { conversation.address },
                            subtitle = conversation.address,
                            trailingText = stringResource(R.string.unblock),
                            onClick = { viewModel.unblockSender(conversation.threadId) },
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.privacy_permissions)) {
                PermissionRow(
                    label = stringResource(R.string.onboarding_permission_sms_title),
                    granted = permissions[PermissionGroup.SMS] == true,
                    onOpen = { openAppSettings(context) },
                )
                PermissionRow(
                    label = stringResource(R.string.onboarding_permission_contacts_title),
                    granted = permissions[PermissionGroup.CONTACTS] == true,
                    onOpen = { openAppSettings(context) },
                )
                PermissionRow(
                    label = stringResource(R.string.onboarding_permission_phone_title),
                    granted = permissions[PermissionGroup.PHONE_STATE] == true,
                    onOpen = { openAppSettings(context) },
                )
                PermissionRow(
                    label = stringResource(R.string.onboarding_permission_notifications_title),
                    granted = permissions[PermissionGroup.NOTIFICATIONS] == true,
                    onOpen = { openAppSettings(context) },
                )
                Text(
                    text = stringResource(R.string.privacy_permissions_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            SettingsSection(stringResource(R.string.privacy_threat_model)) {
                Text(
                    text = stringResource(R.string.privacy_no_upload),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    text = stringResource(R.string.privacy_threat_model_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            SettingsSection(stringResource(R.string.settings_section_data)) {
                SettingsRow(
                    title = stringResource(R.string.privacy_delete_all),
                    subtitle = stringResource(R.string.privacy_delete_all_body),
                    onClick = { confirmClear = true },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.privacy_delete_all_confirm),
            body = stringResource(R.string.privacy_delete_all_body),
            onConfirm = {
                confirmClear = false
                viewModel.clearAppData()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    SettingsRow(
        title = label,
        trailingText = stringResource(
            if (granted) R.string.permission_granted else R.string.permission_denied_short
        ),
        showChevron = true,
        onClick = onOpen,
    )
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
}
