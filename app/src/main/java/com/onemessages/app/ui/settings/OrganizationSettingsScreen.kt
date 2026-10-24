@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.model.ConversationCategory
import com.onemessages.app.ui.components.SettingsSection
import com.onemessages.app.ui.components.SettingsSwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrganizationSettingsRoute(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    viewModel: OrganizationViewModel = hiltViewModel(),
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_organization)) },
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
            SettingsSection(stringResource(R.string.org_categories)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.org_enable),
                    subtitle = stringResource(R.string.org_categories_body),
                    checked = settings.categoriesEnabled,
                    onCheckedChange = settingsViewModel::setCategoriesEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.per_conversation_title),
                    subtitle = stringResource(R.string.read_unread_body),
                    checked = settings.markReadOnOpen,
                    onCheckedChange = settingsViewModel::setMarkReadOnOpen,
                )
            }

            SettingsSection(stringResource(R.string.privacy_otp_detection)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.org_otp_enable),
                    subtitle = stringResource(R.string.privacy_otp_detection_body),
                    checked = settings.otpDetectionEnabled,
                    onCheckedChange = settingsViewModel::setOtpDetection,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.org_hide_codes),
                    subtitle = stringResource(R.string.privacy_hide_otp_body),
                    checked = settings.hideOtpDigits,
                    onCheckedChange = settingsViewModel::setHideOtpDigits,
                    enabled = settings.otpDetectionEnabled,
                )
            }

            SettingsSection(stringResource(R.string.org_rules)) {
                Text(
                    text = stringResource(R.string.org_rules_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (rules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.org_no_rules),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                rules.forEach { rule ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.keyword, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = categoryLabel(rule.category),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { viewModel.toggleRule(rule, it) },
                        )
                        IconButton(onClick = { viewModel.deleteRule(rule) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.org_rule_delete),
                            )
                        }
                    }
                }
                TextButton(onClick = { addDialog = true }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.org_add_rule))
                }
            }

            Text(
                text = stringResource(R.string.org_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addDialog) {
        AddRuleDialog(
            onDismiss = { addDialog = false },
            onAdd = { keyword, category ->
                viewModel.addRule(keyword, category)
                addDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (String, ConversationCategory) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ConversationCategory.PERSONAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.org_add_rule)) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it.take(48) },
                    label = { Text(stringResource(R.string.org_rule_keyword)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.org_rule_category), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ConversationCategory.PERSONAL,
                        ConversationCategory.WORK,
                        ConversationCategory.TRANSACTIONS,
                        ConversationCategory.VERIFICATION,
                    ).forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(categoryLabel(option)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(keyword, category) },
                enabled = keyword.trim().length >= 2,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun categoryLabel(category: ConversationCategory): String = stringResource(
    when (category) {
        ConversationCategory.PERSONAL -> R.string.org_category_personal
        ConversationCategory.WORK -> R.string.org_category_work
        ConversationCategory.TRANSACTIONS -> R.string.org_category_transactions
        ConversationCategory.VERIFICATION -> R.string.org_category_otp
        ConversationCategory.UNKNOWN -> R.string.org_category_unknown
    }
)
