@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.components.Pill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageRoute(
    onBack: () -> Unit,
    onOpenConversation: (threadId: Long, address: String) -> Unit,
    viewModel: NewMessageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(events) {
        when (val event = events) {
            is NewMessageEvent.OpenConversation -> {
                onOpenConversation(event.threadId, event.address)
                viewModel.clearEvent()
            }
            is NewMessageEvent.ShowMessage -> {
                snackbarHostState.showSnackbar(context.getString(event.resId))
                viewModel.clearEvent()
            }
            NewMessageEvent.Sent -> {
                snackbarHostState.showSnackbar(context.getString(R.string.sent))
                viewModel.clearEvent()
                onBack()
            }
            null -> Unit
        }
    }

    LaunchedEffect(state.errorRes) {
        state.errorRes?.let {
            snackbarHostState.showSnackbar(context.getString(it))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.new_message_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(
                    Modifier.navigationBarsPadding().imePadding().padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = state.body,
                            onValueChange = viewModel::onBodyChanged,
                            placeholder = { Text(stringResource(R.string.message_placeholder)) },
                            modifier = Modifier.weight(1f),
                            maxLines = 6,
                            shape = RoundedCornerShape(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = viewModel::send,
                            enabled = state.body.isNotBlank() && state.recipients.isNotEmpty() &&
                                !state.sending,
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Rounded.Send, contentDescription = stringResource(R.string.send))
                            }
                        }
                    }
                    if (state.recipients.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.group_sms_note, state.recipients.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text(stringResource(R.string.to)) },
                placeholder = { Text(stringResource(R.string.recipient_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = viewModel::addTypedRecipient) {
                        Text(stringResource(R.string.add_recipient))
                    }
                },
            )
            if (state.recipients.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.recipients.forEach { recipient ->
                        AssistChip(
                            onClick = { viewModel.removeRecipient(recipient.address) },
                            label = { Text(recipient.displayName ?: recipient.address) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(text = stringResource(R.string.sms_send_hint))
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.group_recipients_title) + " · " +
                            stringResource(R.string.group_recipients_body),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.suggestions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = stringResource(R.string.no_recipients_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.suggestions, key = { it.contactId }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.displayName) },
                            supportingContent = {
                                Text(contact.primaryPhone ?: stringResource(R.string.contact_no_number))
                            },
                            leadingContent = {
                                Avatar(
                                    name = contact.displayName,
                                    photoUri = contact.photoUri,
                                    size = 40.dp,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val phone = contact.primaryPhone
                        if (phone != null) {
                            TextButton(
                                onClick = {
                                    viewModel.addRecipient(
                                        Recipient(
                                            address = phone,
                                            displayName = contact.displayName,
                                            photoUri = contact.photoUri,
                                        )
                                    )
                                },
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                Text(stringResource(R.string.send_to_number, phone))
                            }
                        }
                    }
                }
            }
        }
    }
}
