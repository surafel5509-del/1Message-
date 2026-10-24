@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.conversation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onemessages.app.R
import com.onemessages.app.model.ScheduledMessage
import com.onemessages.app.util.TimeFormatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private data class ScheduleOption(val labelRes: Int, val atMillis: Long)

/**
 * "Send later" chooser.
 *
 * Offers four concrete moments (the ones people actually want: soon, later today, tonight,
 * tomorrow morning) plus the platform date/time dialogs for anything else. Nothing is scheduled
 * until the user presses the confirm button, and the chosen moment is always shown in full so
 * there is no ambiguity about when the message leaves the phone.
 */
@Composable
fun ScheduleSendDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val options = remember(now) { buildOptions(now, zone) }
    var selected by remember { mutableLongStateOf(options.first().atMillis) }

    fun pickDateThenTime() {
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(selected), zone)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        selected = LocalDateTime.of(year, month + 1, day, hour, minute)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                    },
                    start.hour,
                    start.minute,
                    true,
                ).show()
            },
            start.year,
            start.monthValue - 1,
            start.dayOfMonth,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        title = { Text(stringResource(R.string.schedule_send_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.schedule_send_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                options.forEach { option ->
                    val active = selected == option.atMillis
                    Surface(
                        onClick = { selected = option.atMillis },
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(option.labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = TimeFormatters.fullTimestamp(context, option.atMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (active) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Surface(
                    onClick = { pickDateThenTime() },
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.EditCalendar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.schedule_custom),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected) },
                enabled = selected > System.currentTimeMillis(),
            ) {
                Text(stringResource(R.string.schedule_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun buildOptions(now: Long, zone: ZoneId): List<ScheduleOption> {
    val today = LocalDate.now(zone)
    fun at(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    val tonightBase = at(today, 19, 0)
    val tonight = if (tonightBase > now + 60_000L) tonightBase else at(today.plusDays(1), 19, 0)
    val tomorrowBase = at(today.plusDays(1), 8, 0)
    val tomorrow = if (tomorrowBase > now + 60_000L) tomorrowBase else at(today.plusDays(2), 8, 0)

    return listOf(
        ScheduleOption(R.string.schedule_in_one_hour, now + 60 * 60_000L),
        ScheduleOption(R.string.schedule_in_three_hours, now + 3 * 60 * 60_000L),
        ScheduleOption(R.string.schedule_tonight, tonight),
        ScheduleOption(R.string.schedule_tomorrow_morning, tomorrow),
    )
}

/**
 * Bottom sheet listing everything still waiting to send in the current conversation.
 *
 * The body is shown truncated on purpose: the user needs to recognise the message, not read it
 * back, and a long preview would push the actions off screen.
 */
@Composable
fun ScheduledMessagesSheet(
    scheduled: List<ScheduledMessage>,
    onDismiss: () -> Unit,
    onSendNow: (ScheduledMessage) -> Unit,
    onCancel: (ScheduledMessage) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(
                text = stringResource(R.string.scheduled_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text = stringResource(R.string.scheduled_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn {
                items(scheduled, key = { it.id }) { message ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = message.body.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = TimeFormatters.fullTimestamp(context, message.scheduledAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                message.lastError?.let {
                                    Text(
                                        text = stringResource(R.string.scheduled_send_failed),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onCancel(message) }) {
                                    Text(stringResource(R.string.scheduled_cancel))
                                }
                                TextButton(onClick = { onSendNow(message) }) {
                                    Text(stringResource(R.string.scheduled_send_now))
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
