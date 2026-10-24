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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.onemessages.app.ui.theme.LocalAppSettings
import com.onemessages.app.util.TimeFormatters

/**
 * One conversation in the inbox.
 *
 * Hierarchy mirrors how people scan a list: who it is (avatar + name), what they said (snippet),
 * when it happened (right edge), and only then the metadata (unread count, mute, pin, status).
 * Unread rows get a weightier name, a tinted snippet and an accent count badge instead of a
 * colour change on the whole row, which keeps the list calm.
 */
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
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 13.dp)
                .semantics { contentDescription = rowDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(6.dp))
            }
            Box {
                Avatar(
                    name = conversation.displayName,
                    photoUri = conversation.photoUri,
                    size = 52.dp,
                    showPhoto = settings.showAvatars,
                    ring = unread && !selectionMode,
                )
                if (conversation.isPinned && !selectionMode) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.cd_pinned),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conversation.isMuted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.VolumeOff,
                            contentDescription = stringResource(R.string.cd_muted),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = TimeFormatters.inboxTimestamp(context, conversation.lastTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.hasDraft) {
                        Text(
                            text = stringResource(R.string.draft),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                    } else if (conversation.lastDirection == Direction.OUTGOING) {
                        StatusTicks(conversation.lastMessageStatus)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = snippetText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                        color = if (unread) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread) {
                        Spacer(Modifier.width(8.dp))
                        UnreadBadge(count = conversation.unreadCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val label = if (count > 99) "99+" else count.toString()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusTicks(status: MessageStatus) {
    val (icon, description) = when (status) {
        MessageStatus.SENT -> Icons.Rounded.Done to stringResource(R.string.sent)
        MessageStatus.DELIVERED -> Icons.Rounded.DoneAll to stringResource(R.string.delivered)
        MessageStatus.FAILED -> Icons.Rounded.ErrorOutline to stringResource(R.string.failed)
        MessageStatus.SENDING, MessageStatus.QUEUED ->
            Icons.Rounded.Schedule to stringResource(R.string.sending)
        else -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (status == MessageStatus.FAILED) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        },
        modifier = Modifier.size(15.dp),
    )
}
