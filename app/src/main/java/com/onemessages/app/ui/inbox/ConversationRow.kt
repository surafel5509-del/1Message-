@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.onemessages.app.ui.inbox

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onemessages.app.R
import com.onemessages.app.model.Conversation
import com.onemessages.app.model.Direction
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.components.Pill
import com.onemessages.app.ui.theme.LocalAppSettings
import com.onemessages.app.util.TimeFormatters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    conversation: Conversation,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val unread = conversation.isUnread
    val snippetText = when {
        conversation.hasDraft -> conversation.draftText.orEmpty()
        conversation.snippet.isNotBlank() -> conversation.snippet
        conversation.hasAttachmentsInLastMessage -> context.getString(R.string.attach_photo)
        else -> ""
    }
    val rowDescription = context.getString(R.string.cd_open_conversation, conversation.displayName)

    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = rowDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(8.dp))
            }
            Avatar(
                name = conversation.displayName,
                photoUri = conversation.photoUri,
                size = 52.dp,
                showPhoto = settings.showAvatars,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.cd_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conversation.isGroup) {
                        Spacer(Modifier.width(6.dp))
                        Pill(text = conversation.recipientIds.size.toString())
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = TimeFormatters.inboxTimestamp(context, conversation.lastTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.hasDraft) {
                        Text(
                            text = stringResource(R.string.draft) + " · ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (conversation.lastDirection == Direction.OUTGOING &&
                        conversation.lastMessageStatus == MessageStatus.FAILED
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = stringResource(R.string.failed),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = snippetText.ifBlank { stringResource(R.string.conversation_empty_title) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.isMuted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.VolumeOff,
                            contentDescription = stringResource(R.string.cd_muted),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    if (unread && !conversation.isMuted) {
                        Spacer(Modifier.width(8.dp))
                        UnreadBadge(conversation.unreadCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = context.getString(R.string.cd_unread_badge, count) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MessageStateIcon(status: MessageStatus, modifier: Modifier = Modifier) {
    val (icon, description) = when (status) {
        MessageStatus.SENDING, MessageStatus.QUEUED ->
            Icons.Rounded.Schedule to stringResource(R.string.sending)
        MessageStatus.SENT -> Icons.Rounded.Done to stringResource(R.string.sent)
        MessageStatus.DELIVERED -> Icons.Rounded.DoneAll to stringResource(R.string.delivered)
        MessageStatus.FAILED -> Icons.Rounded.ErrorOutline to stringResource(R.string.failed)
        else -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (status == MessageStatus.FAILED) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.size(14.dp),
    )
}
