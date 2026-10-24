@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.conversation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.model.Attachment
import com.onemessages.app.model.ContactIdentity
import com.onemessages.app.model.Direction
import com.onemessages.app.model.Message
import com.onemessages.app.model.SendFailureReason
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.components.ConfirmDialog
import com.onemessages.app.ui.components.ConversationBackground
import com.onemessages.app.ui.components.DateSeparator
import com.onemessages.app.ui.components.MessageBubble
import com.onemessages.app.ui.components.Pill
import com.onemessages.app.util.TimeFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface TimelineEntry {
    data class Day(val key: String, val label: String) : TimelineEntry
    data class Msg(
        val message: Message,
        val groupedWithPrevious: Boolean,
        val showSender: Boolean,
    ) : TimelineEntry
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationRoute(
    hasSmsPermission: Boolean,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onForward: (String) -> Unit,
    onOpenContact: (ContactIdentity) -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var detailsMessage by remember { mutableStateOf<Message?>(null) }
    var confirmDeleteMessage by remember { mutableStateOf<Message?>(null) }
    var confirmDeleteConversation by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingSave by remember { mutableStateOf<Attachment?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showScheduledSheet by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val attachment = pendingSave
        pendingSave = null
        if (uri != null && attachment != null) {
            scope.launch {
                val ok = viewModel.saveAttachment(attachment, uri)
                snackbarHostState.showSnackbar(
                    context.getString(if (ok) R.string.attachment_saved else R.string.attachment_save_failed)
                )
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val transcript = buildTranscript(context, state.displayTitle, state.messages)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(transcript.toByteArray())
                    } ?: error("no stream")
                }.isSuccess
            }
            snackbarHostState.showSnackbar(
                context.getString(
                    if (ok) R.string.export_conversation_done else R.string.export_conversation_failed
                )
            )
        }
    }

    LaunchedEffect(events) {
        when (val event = events) {
            is ConversationEvent.ShowSnackbar -> {
                val text = if (event.arg != null) {
                    context.getString(event.resId, event.arg)
                } else {
                    context.getString(event.resId)
                }
                snackbarHostState.showSnackbar(text)
                viewModel.clearEvent()
            }
            is ConversationEvent.SendFailed -> {
                snackbarHostState.showSnackbar(
                    context.getString(
                        when (event.reason) {
                            SendFailureReason.NO_SIM -> R.string.error_no_sim
                            SendFailureReason.AIRPLANE_MODE,
                            SendFailureReason.NO_SERVICE,
                            SendFailureReason.RADIO_ERROR -> R.string.error_radio_off
                            SendFailureReason.PERMISSION_DENIED -> R.string.permission_denied_body
                            SendFailureReason.INVALID_RECIPIENT -> R.string.invalid_number
                            SendFailureReason.MESSAGE_TOO_LONG -> R.string.error_sms_too_long
                            SendFailureReason.MMS_UNAVAILABLE -> R.string.mms_not_supported_body
                            SendFailureReason.UNKNOWN -> R.string.error_sms_failed
                        }
                    )
                )
                viewModel.clearEvent()
            }
            is ConversationEvent.StartedConversation -> {
                viewModel.clearEvent()
            }
            ConversationEvent.MessageDeleted -> {
                snackbarHostState.showSnackbar(context.getString(R.string.message_deleted))
                viewModel.clearEvent()
            }
            null -> Unit
        }
    }

    val filteredMessages = remember(state.messages, searchQuery) {
        if (searchQuery.isBlank()) {
            state.messages
        } else {
            state.messages.filter { it.body.contains(searchQuery, ignoreCase = true) }
        }
    }

    val timeline = remember(filteredMessages, state.conversation?.isGroup, context) {
        buildTimeline(filteredMessages, state.conversation?.isGroup == true, context)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && listState.firstVisibleItemIndex < 3) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (searchOpen) {
                SearchInConversationBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = { searchOpen = false; searchQuery = "" },
                )
            } else {
                ConversationTopBar(
                    title = state.displayTitle,
                    address = state.address,
                    photoUri = state.contact?.photoUri,
                    showPhoto = state.conversation?.photoUri != null || state.contact?.photoUri != null,
                    isMuted = state.conversation?.isMuted == true,
                    onBack = onBack,
                    onCall = {
                        val number = state.address.ifBlank { state.conversation?.address.orEmpty() }
                        if (number.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                            }.onFailure { /* no dialer: ignore */ }
                        }
                    },
                    onSearch = { searchOpen = true },
                    onOpenContact = {
                        state.contact?.let(onOpenContact)
                    },
                    onMarkUnread = viewModel::markUnread,
                    onToggleMute = viewModel::toggleMute,
                    onTogglePin = viewModel::togglePin,
                    onToggleArchive = viewModel::archiveConversation,
                    onToggleBlock = viewModel::toggleBlock,
                    onDelete = { confirmDeleteConversation = true },
                    onExport = {
                        exportLauncher.launch(
                            "conversation-" + TimeFormatters.fileStamp(System.currentTimeMillis()) + ".txt"
                        )
                    },
                    isBlocked = state.blocked,
                )
            }
        },
        bottomBar = {
            if (hasSmsPermission) {
                ComposerBar(
                    draft = state.draft,
                    sending = state.sending,
                    sims = state.sims,
                    selectedSimId = state.selectedSimId,
                    smartReplies = state.smartReplies,
                    scheduledCount = state.scheduled.size,
                    onDraftChange = viewModel::onDraftChanged,
                    onSelectSim = viewModel::selectSim,
                    onSend = viewModel::send,
                    onAttach = { showAttachmentSheet = true },
                    onScheduleClick = { showScheduleDialog = true },
                    onSmartReply = viewModel::applySmartReply,
                    onOpenScheduled = { showScheduledSheet = true },
                    onBlocked = state.blocked,
                )
            }
        },
    ) { padding ->
        ConversationBackground(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.messages.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                !hasSmsPermission -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.inbox_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                else -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                    ) {
                        item(key = "composer-space") { Spacer(Modifier.height(12.dp)) }
                        val chronological = timeline.reversed()
                        items(
                            items = chronological,
                            key = { entry ->
                                when (entry) {
                                    is TimelineEntry.Day -> "day-${entry.key}"
                                    is TimelineEntry.Msg -> "msg-${entry.message.id}-${entry.message.source}"
                                }
                            },
                        ) { entry ->
                            when (entry) {
                                is TimelineEntry.Day -> DateSeparator(entry.label)
                                is TimelineEntry.Msg -> MessageBubble(
                                    message = entry.message,
                                    senderName = state.displayTitle,
                                    isGroupedWithPrevious = entry.groupedWithPrevious,
                                    isGroupedWithNext = false,
                                    isSelected = false,
                                    selectionMode = false,
                                    showSenderName = entry.showSender,
                                    onLongPress = { actionMessage = entry.message },
                                    onClick = { },
                                    onRetry = { viewModel.retryMessage(entry.message) },
                                    onSaveAttachment = { attachment ->
                                        pendingSave = attachment
                                        saveLauncher.launch(viewModel.suggestedFileName(attachment))
                                    },
                                )
                            }
                        }
                        if (state.hasMore) {
                            item(key = "load-more") {
                                LaunchedEffect(state.messages.firstOrNull()?.id) { viewModel.loadOlder() }
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(20.dp))
                                }
                            }
                        }
                        item(key = "empty-hint") {
                            if (state.messages.isEmpty() && !state.loading) {
                                Column(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        stringResource(R.string.conversation_empty_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.conversation_empty_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (searchOpen && filteredMessages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_search_results))
                        }
                    }
                    if (listState.firstVisibleItemIndex > 6) {
                        FilledIconButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.scroll_to_latest))
                        }
                    }
                }
            }
        }
    }

    // ---- message action sheet -------------------------------------------------------------
    actionMessage?.let { message ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { actionMessage = null },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = TimeFormatters.fullTimestamp(context, message.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SheetAction(Icons.Rounded.ContentCopy, stringResource(R.string.copy_text)) {
                    clipboard.setText(AnnotatedString(message.body))
                    actionMessage = null
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.copied)) }
                }
                SheetAction(Icons.Rounded.Share, stringResource(R.string.share)) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message.body)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                    actionMessage = null
                }
                SheetAction(Icons.Rounded.Forward, stringResource(R.string.forward)) {
                    onForward(message.body)
                    actionMessage = null
                }
                SheetAction(
                    if (message.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(if (message.starred) R.string.unstar else R.string.star),
                ) {
                    viewModel.toggleStar(message)
                    actionMessage = null
                }
                SheetAction(Icons.Rounded.Info, stringResource(R.string.message_info)) {
                    detailsMessage = message
                    actionMessage = null
                }
                SheetAction(
                    Icons.Rounded.Delete,
                    stringResource(R.string.delete),
                    destructive = true,
                ) {
                    confirmDeleteMessage = message
                    actionMessage = null
                }
            }
        }
    }

    detailsMessage?.let { message ->
        MessageDetailsDialog(message = message, onDismiss = { detailsMessage = null })
    }

    confirmDeleteMessage?.let { message ->
        ConfirmDialog(
            title = stringResource(R.string.delete_messages_title),
            body = stringResource(R.string.delete_message_body),
            onConfirm = {
                viewModel.deleteMessage(message)
                confirmDeleteMessage = null
            },
            onDismiss = { confirmDeleteMessage = null },
        )
    }

    if (confirmDeleteConversation) {
        ConfirmDialog(
            title = stringResource(R.string.delete_conversation_title),
            body = stringResource(R.string.delete_conversation_body, state.displayTitle),
            onConfirm = {
                confirmDeleteConversation = false
                viewModel.deleteConversation(onBack)
            },
            onDismiss = { confirmDeleteConversation = false },
        )
    }

    if (showScheduleDialog) {
        ScheduleSendDialog(
            onDismiss = { showScheduleDialog = false },
            onConfirm = { at ->
                showScheduleDialog = false
                viewModel.scheduleSend(at)
            },
        )
    }

    if (showScheduledSheet) {
        ScheduledMessagesSheet(
            scheduled = state.scheduled,
            onDismiss = { showScheduledSheet = false },
            onSendNow = { message ->
                viewModel.sendScheduledNow(message)
                showScheduledSheet = false
            },
            onCancel = viewModel::cancelScheduled,
        )
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            onDismiss = { showAttachmentSheet = false },
            onShare = { uris ->
                showAttachmentSheet = false
                uris.firstOrNull()?.let { uri ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = context.contentResolver.getType(uri) ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                        .onFailure { error ->
                            if (error is ActivityNotFoundException) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.attachment_unsupported))
                                }
                            }
                        }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopBar(
    title: String,
    address: String,
    photoUri: String?,
    showPhoto: Boolean,
    isMuted: Boolean,
    isBlocked: Boolean,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onSearch: () -> Unit,
    onOpenContact: () -> Unit,
    onMarkUnread: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleBlock: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Avatar(name = title, photoUri = photoUri, size = 38.dp, showPhoto = showPhoto)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMuted) {
                            Icon(
                                Icons.Rounded.VolumeOff,
                                contentDescription = stringResource(R.string.cd_muted),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = onCall) {
                    Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.call))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.contact_details)) },
                            onClick = { menuOpen = false; onOpenContact() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_unread)) },
                            onClick = { menuOpen = false; onMarkUnread() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isMuted) R.string.unmute else R.string.mute)) },
                            onClick = { menuOpen = false; onToggleMute() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pin)) },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                            onClick = { menuOpen = false; onTogglePin() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.archive)) },
                            onClick = { menuOpen = false; onToggleArchive() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isBlocked) R.string.per_conversation_unblock
                                        else R.string.per_conversation_block
                                    )
                                )
                            },
                            onClick = { menuOpen = false; onToggleBlock() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_conversation)) },
                            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            onClick = { menuOpen = false; onExport() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInConversationBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.close))
            }
        },
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_in_conversation)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun MessageDetailsDialog(message: Message, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_info)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (message.direction == Direction.OUTGOING) R.string.you_me else R.string.received
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(TimeFormatters.fullTimestamp(context, message.timestamp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (message.status) {
                        com.onemessages.app.model.MessageStatus.DELIVERED -> stringResource(R.string.delivered)
                        com.onemessages.app.model.MessageStatus.SENT -> stringResource(R.string.sent)
                        com.onemessages.app.model.MessageStatus.FAILED -> stringResource(R.string.failed)
                        com.onemessages.app.model.MessageStatus.SENDING -> stringResource(R.string.sending)
                        else -> stringResource(R.string.received)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (message.subscriptionId >= 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.message_sent_from_sim, message.subscriptionId.toString()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (message.source == com.onemessages.app.model.MessageSource.MMS) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.attach_photo), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onShare: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pickedUris = pickedUris + it } }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pickedUris = pickedUris + it } }
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pickedUris = pickedUris + it } }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.attachment_picker_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.mms_not_supported_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.attach_photo)) }
                TextButton(onClick = {
                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) { Text(stringResource(R.string.attach_video)) }
                TextButton(onClick = {
                    pickDocument.launch(arrayOf("audio/*", "application/pdf", "text/*"))
                }) { Text(stringResource(R.string.attach_document)) }
            }
            if (pickedUris.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                pickedUris.forEach { uri ->
                    val meta = remember(uri) {
                        runCatching {
                            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (c.moveToFirst()) {
                                    (if (nameIdx >= 0) c.getString(nameIdx) else null) to
                                        (if (sizeIdx >= 0) c.getLong(sizeIdx) else null)
                                } else {
                                    null to null
                                }
                            }
                        }.getOrNull()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(meta?.first ?: "attachment", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                text = meta?.second?.let { com.onemessages.app.ui.components.formatSize(it) }
                                    ?: stringResource(R.string.attachment_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { pickedUris = pickedUris - uri }) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(text = stringResource(R.string.attachment_preview))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { pickedUris = emptyList() }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = { onShare(pickedUris) }) {
                        Text(stringResource(R.string.share))
                    }
                }
            }
        }
    }
}

private fun buildTimeline(
    messages: List<Message>,
    isGroup: Boolean,
    context: android.content.Context,
): List<TimelineEntry> {
    if (messages.isEmpty()) return emptyList()
    val entries = ArrayList<TimelineEntry>(messages.size + 4)
    var previousDay: String? = null
    var previous: Message? = null
    for (message in messages) {
        val day = TimeFormatters.dayKey(message.timestamp).toString()
        if (day != previousDay) {
            entries += TimelineEntry.Day(
                key = day,
                label = TimeFormatters.dateSeparator(context, message.timestamp),
            )
            previousDay = day
            previous = null
        }
        val grouped = previous != null &&
            previous.direction == message.direction &&
            previous.source == message.source &&
            kotlin.math.abs(message.timestamp - previous.timestamp) < 2 * 60 * 1000
        entries += TimelineEntry.Msg(
            message = message,
            groupedWithPrevious = grouped,
            showSender = isGroup && message.direction == Direction.INCOMING,
        )
        previous = message
    }
    return entries
}

/** Simple, transparent counter matching how Android splits SMS: 160 for one part, 153 after. */

/**
 * Plain-text transcript for the share/save flow.
 *
 * Built only when the user asks for it and written straight to the document they picked, so the
 * text never passes through an app cache, log or third party.
 */
private fun buildTranscript(
    context: android.content.Context,
    title: String,
    messages: List<Message>,
): String {
    val builder = StringBuilder()
    builder.append(title).append('\n')
    builder.append(context.getString(R.string.export_conversation)).append('\n')
    builder.append("\n")
    val sorted = messages.sortedBy { it.timestamp }
    sorted.forEach { message ->
        val who = if (message.direction == Direction.OUTGOING) "me" else title
        builder.append(TimeFormatters.fullTimestamp(context, message.timestamp))
        builder.append(" · ").append(who)
        builder.append('\n')
        builder.append(message.body)
        builder.append("\n\n")
    }
    return builder.toString()
}
