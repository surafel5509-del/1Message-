@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.onemessages.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemessages.app.R
import com.onemessages.app.model.Attachment
import com.onemessages.app.model.AttachmentKind
import com.onemessages.app.model.Direction
import com.onemessages.app.model.Message
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.ui.theme.Bubbles
import com.onemessages.app.ui.theme.LocalConversationColors
import com.onemessages.app.ui.theme.LocalMessageSpacing
import com.onemessages.app.util.TimeFormatters

/**
 * A single message bubble.
 *
 * Grouping rules: consecutive messages from the same side within two minutes share a visual
 * run (tighter spacing, squared inner corner). Long-press opens the message action sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    senderName: String,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    showSenderName: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onSaveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalConversationColors.current
    val spacing = LocalMessageSpacing.current
    val outgoing = message.direction == Direction.OUTGOING
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp * spacing.maxBubbleWidthFraction).dp
    val bubbleColor = if (outgoing) colors.outgoingBubble else colors.incomingBubble
    val textColor = if (outgoing) colors.outgoingText else colors.incomingText
    val shape = when {
        outgoing && isGroupedWithPrevious -> RoundedCornerShape(
            topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp
        )
        outgoing -> Bubbles.outgoing
        isGroupedWithPrevious -> RoundedCornerShape(
            topStart = 6.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp
        )
        else -> Bubbles.incoming
    }
    val outgoingDescription = stringResource(R.string.cd_message_from_you, message.previewText)
    val incomingDescription = stringResource(R.string.cd_message_from_them, senderName, message.previewText)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = spacing.horizontalPadding.dp,
                vertical = if (isGroupedWithPrevious) (spacing.groupedGap / 2).dp else (spacing.verticalGap / 2).dp,
            ),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        if (!outgoing && showSenderName) {
            Avatar(name = senderName, photoUri = null, size = 28.dp, showPhoto = false)
            Spacer(Modifier.width(8.dp))
        } else if (!outgoing) {
            Spacer(Modifier.width(36.dp))
        }
        Column(
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else bubbleColor)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .semantics {
                    contentDescription = if (outgoing) outgoingDescription else incomingDescription
                },
        ) {
            if (!outgoing && showSenderName) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(3.dp))
            }
            message.subject?.takeIf { it.isNotBlank() }?.let { subject ->
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (message.body.isNotBlank()) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
            }
            if (message.attachments.isNotEmpty()) {
                Spacer(Modifier.height(if (message.body.isBlank()) 0.dp else 8.dp))
                message.attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        tint = textColor,
                        onSave = { onSaveAttachment(attachment) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.starred) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = stringResource(R.string.cd_starred),
                        tint = if (outgoing) colors.outgoingTimestamp else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = TimeFormatters.messageTime(androidx.compose.ui.platform.LocalContext.current, message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outgoing) colors.outgoingTimestamp else colors.incomingTimestamp,
                )
                if (outgoing) {
                    Spacer(Modifier.width(6.dp))
                    DeliveryStatus(
                        status = message.status,
                        tint = colors.outgoingTimestamp,
                        errorTint = MaterialTheme.colorScheme.error,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatus(
    status: MessageStatus,
    tint: Color,
    errorTint: Color,
    onRetry: () -> Unit,
) {
    val (icon, description) = when (status) {
        MessageStatus.SENDING, MessageStatus.QUEUED -> Icons.Rounded.Schedule to stringResource(R.string.sending)
        MessageStatus.SENT -> Icons.Rounded.Done to stringResource(R.string.sent)
        MessageStatus.DELIVERED -> Icons.Rounded.DoneAll to stringResource(R.string.delivered)
        MessageStatus.FAILED -> Icons.Rounded.ErrorOutline to stringResource(R.string.failed)
        else -> return
    }
    if (status == MessageStatus.FAILED) {
        TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.cd_failed_send),
                tint = errorTint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                stringResource(R.string.failed_tap_to_retry),
                style = MaterialTheme.typography.labelSmall,
                color = errorTint,
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(description, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment, tint: Color, onSave: () -> Unit) {
    val label = attachment.fileName
        ?: attachment.contentType.substringAfterLast('/').uppercase()
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconFor(attachment.kind),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = tint, maxLines = 1)
                    Text(
                        text = if (attachment.isPendingDownload) {
                            stringResource(R.string.mms_pending_download)
                        } else {
                            attachment.sizeBytes?.let { formatSize(it) } ?: stringResource(R.string.attachment_failed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = tint.copy(alpha = 0.7f),
                    )
                }
                TextButton(onClick = onSave, enabled = !attachment.isPendingDownload) {
                    Text(stringResource(R.string.attachment_save), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (attachment.kind == AttachmentKind.IMAGE && !attachment.isPendingDownload) {
                val loaded = rememberImageBitmapState(attachment.uriString).value
                if (loaded != null) {
                    androidx.compose.foundation.Image(
                        bitmap = loaded,
                        contentDescription = stringResource(R.string.cd_attachment, label),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
    }
}

private fun iconFor(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.IMAGE -> Icons.Rounded.Image
    AttachmentKind.VIDEO -> Icons.Rounded.Movie
    AttachmentKind.AUDIO -> Icons.Rounded.Audiotrack
    AttachmentKind.VCARD -> Icons.Rounded.Person
    AttachmentKind.DOCUMENT -> Icons.Rounded.Description
    AttachmentKind.OTHER -> Icons.Rounded.AttachFile
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Date pill between message runs. */
@Composable
fun DateSeparator(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
