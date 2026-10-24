@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemessages.app.R
import com.onemessages.app.domain.SmartReplyKind
import com.onemessages.app.model.SimInfo
import com.onemessages.app.ui.theme.auroraBrush
import com.onemessages.app.util.SmsSegments

/**
 * Message composer.
 *
 * Three ideas drive the layout: the field is a soft pill that floats above the keyboard, every
 * secondary affordance (attachment, SIM, send later) stays inside the pill so the send button is
 * always in the same place, and suggestions appear only while the field is empty so they never
 * push the text the user is writing around.
 */
@Composable
fun ComposerBar(
    draft: String,
    sending: Boolean,
    sims: List<SimInfo>,
    selectedSimId: Int,
    smartReplies: List<SmartReplyKind>,
    scheduledCount: Int,
    onDraftChange: (String) -> Unit,
    onSelectSim: (Int) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onScheduleClick: () -> Unit,
    onSmartReply: (String) -> Unit,
    onOpenScheduled: () -> Unit,
    onBlocked: Boolean,
) {
    var simMenuOpen by remember { mutableStateOf(false) }
    val segments = remember(draft) { SmsSegments.estimate(draft) }
    val dark = isSystemInDarkTheme()
    val canSend = draft.isNotBlank() && !sending && sims.isNotEmpty() && !onBlocked

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(top = 6.dp, bottom = 10.dp),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 40.dp),
            )

            if (scheduledCount > 0) {
                ScheduledChip(count = scheduledCount, onClick = onOpenScheduled)
            }

            if (draft.isBlank() && smartReplies.isNotEmpty() && !onBlocked) {
                SmartReplyRow(replies = smartReplies, onPick = onSmartReply)
            }

            if (draft.length > 100) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = if (segments > 1) {
                            stringResource(R.string.composer_segments, draft.length, segments)
                        } else {
                            stringResource(R.string.composer_chars, draft.length)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        IconButton(onClick = onAttach, enabled = !onBlocked) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = stringResource(R.string.attach),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                            placeholder = {
                                Text(
                                    stringResource(R.string.message_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            maxLines = 6,
                            shape = RoundedCornerShape(22.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            enabled = !onBlocked,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                        )
                        if (draft.isNotBlank()) {
                            IconButton(onClick = onScheduleClick) {
                                Icon(
                                    Icons.Rounded.Schedule,
                                    contentDescription = stringResource(R.string.schedule_send_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                SendButton(enabled = canSend, sending = sending, dark = dark, onClick = onSend)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp),
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)),
                    ) {
                        TextButton(
                            onClick = { simMenuOpen = true },
                            enabled = sims.size > 1 && !onBlocked,
                        ) {
                            Icon(
                                Icons.Rounded.Call,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = sims.firstOrNull { it.subscriptionId == selectedSimId }?.let { sim ->
                                    if (sims.size > 1) {
                                        stringResource(R.string.sim_number_short, sim.slotIndex + 1)
                                    } else {
                                        sim.label
                                    }
                                } ?: stringResource(R.string.sim_none),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(expanded = simMenuOpen, onDismissRequest = { simMenuOpen = false }) {
                        sims.forEach { sim ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.sim_number,
                                            sim.slotIndex + 1,
                                            sim.label,
                                        )
                                    )
                                },
                                onClick = {
                                    onSelectSim(sim.subscriptionId)
                                    simMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.sms_send_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
internal fun SendButton(enabled: Boolean, sending: Boolean, dark: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    val modifier = Modifier
        .size(52.dp)
        .clip(shape)
        .background(
            if (enabled || sending) {
                auroraBrush(dark)
            } else {
                androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
            },
            shape,
        )
        .clickable(enabled = enabled, onClick = onClick)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                Icons.Rounded.Send,
                contentDescription = stringResource(R.string.cd_send_message),
                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ScheduledChip(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.scheduled_banner, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Horizontally scrolling reply suggestions, shown only while the composer is empty. */
@Composable
private fun SmartReplyRow(replies: List<SmartReplyKind>, onPick: (String) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 2.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(replies, key = { it.name }) { kind ->
            val label = stringResource(smartReplyRes(kind))
            Surface(
                onClick = { onPick(label) },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

internal fun smartReplyRes(kind: SmartReplyKind): Int = when (kind) {
    SmartReplyKind.YES -> R.string.smart_reply_yes
    SmartReplyKind.NO -> R.string.smart_reply_no
    SmartReplyKind.THANKS -> R.string.smart_reply_thanks
    SmartReplyKind.OK -> R.string.smart_reply_ok
    SmartReplyKind.ON_MY_WAY -> R.string.smart_reply_on_my_way
    SmartReplyKind.CALL_ME -> R.string.smart_reply_call_me
    SmartReplyKind.GREETING -> R.string.smart_reply_greeting
    SmartReplyKind.SEE_YOU_SOON -> R.string.smart_reply_see_you_soon
}
