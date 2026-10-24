@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.BuildConfigCompat
import com.onemessages.app.R
import com.onemessages.app.model.SmsRoleState
import com.onemessages.app.ui.components.SettingsRow
import com.onemessages.app.ui.components.SettingsSection

@Composable
fun SettingsHomeRoute(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSim: () -> Unit,
    onOpenOrganization: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onRequestRole: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val role by viewModel.roleState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
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
            RoleCard(role = role, onRequestRole = onRequestRole)
            SettingsSection(stringResource(R.string.settings_section_general)) {
                SettingsRow(
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(R.string.settings_appearance_summary),
                    icon = Icons.Rounded.Palette,
                    showChevron = true,
                    onClick = onOpenAppearance,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_summary),
                    icon = Icons.Rounded.Notifications,
                    showChevron = true,
                    onClick = onOpenNotifications,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    title = stringResource(R.string.settings_sim),
                    subtitle = stringResource(R.string.settings_sim_summary),
                    icon = Icons.Rounded.SimCard,
                    showChevron = true,
                    onClick = onOpenSim,
                )
            }
            SettingsSection(stringResource(R.string.settings_privacy)) {
                SettingsRow(
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_summary),
                    icon = Icons.Rounded.Lock,
                    trailingText = if (settings.appLockEnabled) {
                        stringResource(R.string.privacy_app_lock)
                    } else {
                        null
                    },
                    showChevron = true,
                    onClick = onOpenPrivacy,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    title = stringResource(R.string.settings_organization),
                    subtitle = stringResource(R.string.settings_organization_summary),
                    icon = Icons.Rounded.Tune,
                    showChevron = true,
                    onClick = onOpenOrganization,
                )
            }
            SettingsSection(stringResource(R.string.settings_section_data)) {
                SettingsRow(
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_summary),
                    icon = Icons.Rounded.Backup,
                    showChevron = true,
                    onClick = onOpenBackup,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsRow(
                    title = stringResource(R.string.settings_storage),
                    subtitle = stringResource(R.string.settings_storage_summary),
                    icon = Icons.Rounded.Storage,
                    showChevron = true,
                    onClick = onOpenStorage,
                )
            }
            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_version, "1.0.0", 1),
                    icon = Icons.Rounded.Info,
                    showChevron = true,
                    onClick = onOpenAbout,
                )
            }
            Text(
                text = stringResource(R.string.settings_legal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoleCard(role: SmsRoleState, onRequestRole: () -> Unit) {
    val context = LocalContext.current
    val isDefault = role == SmsRoleState.DEFAULT_SMS_APP
    Surface(
        color = if (isDefault) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (isDefault) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_default_role),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDefault) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (isDefault) R.string.settings_default_role_active
                    else R.string.settings_default_role_inactive
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDefault) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            )
            Text(
                text = stringResource(R.string.settings_default_role_body),
                style = MaterialTheme.typography.bodySmall,
                color = if (isDefault) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                },
            )
            if (!isDefault) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRequestRole) {
                        Text(stringResource(R.string.settings_default_role_cta))
                    }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            )
                        }
                    }) {
                        Text(stringResource(R.string.open_settings))
                    }
                }
            }
        }
    }
}
