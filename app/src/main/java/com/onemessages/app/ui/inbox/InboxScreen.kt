@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.model.Conversation
import com.onemessages.app.model.InboxFilter
import com.onemessages.app.model.SmsRoleState
import com.onemessages.app.ui.components.ConfirmDialog
import com.onemessages.app.ui.components.EmptyState
import com.onemessages.app.ui.components.ErrorState
import com.onemessages.app.ui.components.PermissionCard
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star

private const val EMPTY_TOP_PADDING = 72

@Composable
fun InboxRoute(
    filterArg: String?,
    onOpenConversation: (threadId: Long, address: String) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSettings: () -> Unit,
    hasSmsPermission: Boolean,
    roleState: SmsRoleState,
    onRequestSmsPermission: () -> Unit,
    onRequestRole: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    LaunchedEffect(filterArg) {
        val target = when (filterArg) {
            "archived" -> InboxFilter.ARCHIVED
            "starred" -> InboxFilter.STARRED
            "unread" -> InboxFilter.UNREAD
            "unknown" -> InboxFilter.UNKNOWN
            null -> return@LaunchedEffect
            else -> InboxFilter.ALL
        }
        viewModel.setFilter(target)
    }

    LaunchedEffect(events) {
        when (val event = events) {
            is InboxEvent.Deleted -> {
                snackbarHostState.showSnackbar(
                    context.resources.getQuantityString(
                        R.plurals.conversations_deleted, event.count, event.count
                    )
                )
                viewModel.clearEvent()
            }
            is InboxEvent.Info -> {
                snackbarHostState.showSnackbar(
                    if (event.count > 0) {
                        context.getString(event.resId, event.count)
                    } else {
                        context.getString(event.resId)
                    }
                )
                viewModel.clearEvent()
            }
            null -> Unit
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isSelecting && hasSmsPermission) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.compose_new)) },
                    modifier = Modifier.padding(4.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isSelecting) {
                SelectionTopBar(
                    count = state.selection.size,
                    onClose = viewModel::clearSelection,
                    onMarkRead = { viewModel.markSelectedRead(true) },
                    onArchive = { viewModel.archiveSelected(true) },
                    onMute = { viewModel.muteSelected(true) },
                    onDelete = { confirmBulkDelete = true },
                )
            } else {
                InboxTopBar(
                    unread = state.totalUnread,
                    filter = state.filter,
                    onSearch = onSearch,
                    onSortUnreadFirst = {
                        viewModel.setSort(
                            if (state.sort == com.onemessages.app.model.ConversationSort.RECENT) {
                                com.onemessages.app.model.ConversationSort.UNREAD_FIRST
                            } else {
                                com.onemessages.app.model.ConversationSort.RECENT
                            }
                        )
                    },
                    onOpenArchived = onOpenArchived,
                    onSettings = onOpenSettings,
                )
            }

            if (!hasSmsPermission) {
                PermissionCard(
                    title = stringResource(R.string.inbox_permission_title),
                    body = stringResource(R.string.inbox_permission_body),
                    actionLabel = stringResource(R.string.onboarding_grant_permissions),
                    onAction = onRequestSmsPermission,
                )
            }
            if (roleState == SmsRoleState.NOT_DEFAULT) {
                RoleBanner(onRequestRole)
            }

            FilterRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )

            Box(Modifier.weight(1f)) {
                when {
                    state.loading && state.conversations.isEmpty() -> LoadingState()
                    state.error -> ErrorState(
                        title = stringResource(R.string.inbox_error_title),
                        body = stringResource(R.string.inbox_error_body),
                        onAction = viewModel::refresh,
                    )
                    state.conversations.isEmpty() -> InboxEmptyState(
                        filter = state.filter,
                        hasPermission = hasSmsPermission,
                        onCompose = onCompose,
                        onClearFilter = { viewModel.setFilter(InboxFilter.ALL) },
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(
                            items = state.conversations,
                            key = { conversation -> conversation.threadId },
                            contentType = { "conversation" },
                        ) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                isSelected = conversation.threadId in state.selection,
                                selectionMode = state.isSelecting,
                                onClick = {
                                    if (state.isSelecting) {
                                        viewModel.toggleSelection(conversation.threadId)
                                    } else {
                                        onOpenConversation(conversation.threadId, conversation.address)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(conversation.threadId) },
                            )
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
    }

    if (confirmBulkDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_selected_conversations_title, state.selection.size),
            body = stringResource(R.string.delete_selected_conversations_body),
            onConfirm = {
                viewModel.deleteSelected()
                confirmBulkDelete = false
            },
            onDismiss = { confirmBulkDelete = false },
        )
    }
}

@Composable
private fun InboxTopBar(
    unread: Int,
    filter: InboxFilter,
    onSearch: () -> Unit,
    onSortUnreadFirst: () -> Unit,
    onOpenArchived: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when (filter) {
                    InboxFilter.ARCHIVED -> stringResource(R.string.inbox_archived_title)
                    InboxFilter.STARRED -> stringResource(R.string.starred_title)
                    else -> stringResource(R.string.inbox_title)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (unread > 0) {
                Text(
                    text = stringResource(R.string.inbox_unread_count, unread),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.inbox_menu_sort)) },
                    onClick = { menuOpen = false; onSortUnreadFirst() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.inbox_filter_archived)) },
                    onClick = { menuOpen = false; onOpenArchived() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_settings)) },
                    onClick = { menuOpen = false; onSettings() },
                )
            }
        }
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.selected_count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMarkRead) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.mark_read))
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Rounded.Archive, contentDescription = stringResource(R.string.archive))
            }
            IconButton(onClick = onMute) {
                Icon(Icons.Rounded.VolumeOff, contentDescription = stringResource(R.string.mute))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RoleBanner(onRequestRole: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.onboarding_not_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRequestRole) {
                Text(stringResource(R.string.onboarding_role_set))
            }
        }
    }
}

@Composable
private fun FilterRow(selected: InboxFilter, onSelect: (InboxFilter) -> Unit) {
    val filters = listOf(
        InboxFilter.ALL to R.string.inbox_filter_all,
        InboxFilter.UNREAD to R.string.inbox_filter_unread,
        InboxFilter.STARRED to R.string.inbox_filter_starred,
        InboxFilter.UNKNOWN to R.string.inbox_filter_unknown,
        InboxFilter.ARCHIVED to R.string.inbox_filter_archived,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (filter, labelRes) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(labelRes), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InboxEmptyState(
    filter: InboxFilter,
    hasPermission: Boolean,
    onCompose: () -> Unit,
    onClearFilter: () -> Unit,
) {
    val filtered = filter != InboxFilter.ALL
    EmptyState(
        title = if (filtered) {
            stringResource(R.string.inbox_empty_filtered_title)
        } else {
            stringResource(R.string.inbox_empty_title)
        },
        body = when {
            filter == InboxFilter.ARCHIVED -> stringResource(R.string.inbox_archived_empty)
            filtered -> stringResource(R.string.inbox_empty_filtered_body)
            else -> stringResource(R.string.inbox_empty_body)
        },
        icon = when (filter) {
            InboxFilter.STARRED -> Icons.Rounded.Star
            InboxFilter.UNKNOWN -> Icons.Rounded.Lock
            else -> Icons.Rounded.ChatBubbleOutline
        },
        actionLabel = if (filtered) {
            stringResource(R.string.inbox_filter_all)
        } else if (hasPermission) {
            stringResource(R.string.compose_new)
        } else {
            null
        },
        onAction = if (filtered) onClearFilter else if (hasPermission) onCompose else null,
        modifier = Modifier.padding(top = EMPTY_TOP_PADDING.dp),
    )
}
