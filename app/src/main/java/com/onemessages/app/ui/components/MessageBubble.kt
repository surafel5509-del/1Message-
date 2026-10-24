@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.onemessages.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
 * Runs: consecutive messages from the same side within a couple of minutes share a visual block.
 * The corner facing the sender squares off across the run, the last bubble of a run carries the
 * timestamp and delivery state, and the whole run sits on the same side of the thread.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val context = LocalContext.current
    val outgoing = message.direction == Direction.OUTGOING
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp * spacing.maxBubbleWidthFraction).dp
    val shape = if (outgoing) {
        Bubbles.outgoing(groupedAbove = isGroupedWithPrevious, groupedBelow = isGroupedWithNext)
    } else {
        Bubbles.incoming(groupedAbove = isGroupedWithPrevious, groupedBelow = isGroupedWithNext)
    }

    val bubbleColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.otpChip
            outgoing -> colors.outgoingBubble
            else -> colors.incomingBubble
        },
        label = "bubbleColor",
    )
    val textColor = when {
        isSelected -> colors.otpChipText
        outgoing -> colors.outgoingText
        else -> colors.incomingText
    }
    // A barely-there gradient stops large bubbles from looking like flat stickers.
    val bubbleBrush = Brush.verticalGradient(
        listOf(
            bubbleColor,
            lerp(bubbleColor, if (outgoing) Color.Black else Color.Black, 0.045f),
        )
    )
    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 6f else if (outgoing) 2f else 1f,
        label = "bubbleElevation",
    )

    val outgoingDescription = stringResource(R.string.cd_message_from_you, message.previewText)
    val incomingDescription = stringResource(R.string.cd_message_from_them, senderName, message.previewText)
    val timestampColor = if (outgoing) colors.outgoingTimestamp else colors.incomingTimestamp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = spacing.horizontalPadding.dp,
                vertical = if (isGroupedWithPrevious) {
                    (spacing.groupedGap / 2).dp
                } else {
                    (spacing.verticalGap / 2).dp + 1.dp
                },
                top = if (!isGroupedWithPrevious && !outgoing) 3.dp else 0.dp,
            ),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        if (!outgoing && showSenderName) {
            Avatar(name = senderName, photoUri = null, size = 30.dp, showPhoto = false)
            Spacer(Modifier.width(8.dp))
        } else if (!outgoing) {
            Spacer(Modifier.width(38.dp))
        }

        Surface(
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = elevation.dp,
            border = if (selectionMode && isSelected) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bubbleBrush)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .semantics {
                    contentDescription = if (outgoing) outgoingDescription else incomingDescription
                },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                if (!outgoing && showSenderName) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelLarge,
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
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (message.starred) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = stringResource(R.string.cd_starred),
                            tint = timestampColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = TimeFormatters.messageTime(context, message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = timestampColor,
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(5.dp))
                        DeliveryStatus(
                            status = message.status,
                            tint = timestampColor,
                            errorTint = MaterialTheme.colorScheme.error,
                            onRetry = onRetry,
                        )
                    }
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
        MessageStatus.SENDING, MessageStatus.QUEUED ->
            Icons.Rounded.Schedule to stringResource(R.string.sending)
        MessageStatus.SENT -> Icons.Rounded.Done to stringResource(R.string.sent)
        MessageStatus.DELIVERED -> Icons.Rounded.DoneAll to stringResource(R.string.delivered)
        MessageStatus.FAILED -> Icons.Rounded.ErrorOutline to stringResource(R.string.failed)
        else -> return
    }
    if (status == MessageStatus.FAILED) {
        TextButton(
            onClick = onRetry,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.cd_failed_send),
                tint = errorTint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                stringResource(R.string.retry),
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
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment, tint: Color, onSave: () -> Unit) {
    val label = attachment.fileName
        ?: attachment.contentType.substringAfterLast('/').uppercase()
    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
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
                            attachment.sizeBytes?.let { formatSize(it) }
                                ?: stringResource(R.string.attachment_failed)
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
                    Image(
                        bitmap = loaded,
                        contentDescription = stringResource(R.string.cd_attachment, label),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp)),
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

/** Date pill between message runs, with hairlines so it reads as a divider, not a message. */
@Composable
fun DateSeparator(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    }
}
