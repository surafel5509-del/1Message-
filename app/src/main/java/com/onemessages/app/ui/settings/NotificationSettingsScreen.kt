@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.notifications.QuietHours
import com.onemessages.app.ui.components.SettingsRow
import com.onemessages.app.ui.components.SettingsSection
import com.onemessages.app.ui.components.SettingsSwitchRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsRoute(
    onBack: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)

    val soundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        viewModel.setNotificationSound(uri?.toString())
    }

    LaunchedEffect(testResult) {
        if (testResult == false) {
            snackbarHostState.showSnackbar(context.getString(R.string.notif_permission_needed))
            viewModel.clearTestResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
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
            SettingsSection(stringResource(R.string.notif_channel_messages)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_new_messages),
                    subtitle = stringResource(R.string.notif_settings_new_messages_body),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_preview),
                    subtitle = stringResource(R.string.notif_settings_preview_body),
                    checked = settings.showMessagePreview,
                    onCheckedChange = viewModel::setShowPreview,
                    enabled = settings.notificationsEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_lock_screen),
                    subtitle = stringResource(R.string.notif_settings_lock_screen_body),
                    checked = settings.hideContentOnLockScreen,
                    onCheckedChange = viewModel::setHideOnLock,
                    enabled = settings.notificationsEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_group),
                    subtitle = stringResource(R.string.notif_settings_group_body),
                    checked = settings.groupNotifications,
                    onCheckedChange = viewModel::setGroupNotifications,
                    enabled = settings.notificationsEnabled,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_vibration),
                    subtitle = stringResource(R.string.notif_settings_vibration_body),
                    checked = settings.vibrationEnabled,
                    onCheckedChange = viewModel::setVibration,
                    enabled = settings.notificationsEnabled,
                )
                SettingsRow(
                    title = stringResource(R.string.notif_settings_sound),
                    subtitle = settings.notificationSound ?: stringResource(R.string.per_conversation_default_sound),
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.notificationSound?.let(Uri::parse),
                            )
                        }
                        runCatching { soundLauncher.launch(intent) }
                    },
                    showChevron = true,
                )
            }

            SettingsSection(stringResource(R.string.notif_settings_reminders)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_reminders),
                    subtitle = stringResource(R.string.notif_settings_reminders_body),
                    checked = settings.remindersEnabled,
                    onCheckedChange = viewModel::setRemindersEnabled,
                )
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    listOf(15 to R.string.notif_delay_15, 30 to R.string.notif_delay_30, 60 to R.string.notif_delay_60, 180 to R.string.notif_delay_180)
                        .forEach { (minutes, label) ->
                            FilterChip(
                                selected = settings.reminderDelayMinutes == minutes,
                                onClick = { viewModel.setReminderDelay(minutes) },
                                label = { Text(stringResource(label)) },
                                enabled = settings.remindersEnabled,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                }
            }

            SettingsSection(stringResource(R.string.notif_settings_quiet_hours)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_quiet_enabled),
                    subtitle = stringResource(R.string.notif_settings_quiet_hours_body),
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = viewModel::setQuietHoursEnabled,
                )
                SettingsRow(
                    title = stringResource(R.string.notif_settings_quiet_start),
                    trailingText = QuietHours.formatMinuteOfDay(settings.quietStartMinute, is24Hour),
                    enabled = settings.quietHoursEnabled,
                    onClick = {
                        showTimePicker(context, settings.quietStartMinute) { minute ->
                            viewModel.setQuietStart(minute)
                        }
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.notif_settings_quiet_end),
                    trailingText = QuietHours.formatMinuteOfDay(settings.quietEndMinute, is24Hour),
                    enabled = settings.quietHoursEnabled,
                    onClick = {
                        showTimePicker(context, settings.quietEndMinute) { minute ->
                            viewModel.setQuietEnd(minute)
                        }
                    },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.notif_settings_priority),
                    subtitle = stringResource(R.string.notif_settings_priority_body),
                    checked = settings.priorityNotifications,
                    onCheckedChange = viewModel::setPriorityNotifications,
                )
            }

            SettingsSection(stringResource(R.string.settings_section_general)) {
                SettingsRow(
                    title = stringResource(R.string.notif_test_action),
                    subtitle = stringResource(R.string.notif_test_body),
                    onClick = { viewModel.sendTestNotification() },
                )
                SettingsRow(
                    title = stringResource(R.string.notif_settings_system),
                    subtitle = stringResource(R.string.notif_settings_system_body),
                    onClick = {
                        runCatching {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            }
                            context.startActivity(intent)
                        }
                        onOpenSystemNotificationSettings()
                    },
                    showChevron = true,
                )
            }
            Text(
                text = stringResource(R.string.notif_settings_dnd_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun showTimePicker(
    context: android.content.Context,
    initialMinuteOfDay: Int,
    onPicked: (Int) -> Unit,
) {
    val hour = initialMinuteOfDay / 60
    val minute = initialMinuteOfDay % 60
    android.app.TimePickerDialog(
        context,
        { _, pickedHour, pickedMinute -> onPicked(pickedHour * 60 + pickedMinute) },
        hour,
        minute,
        android.text.format.DateFormat.is24HourFormat(context),
    ).show()
}
