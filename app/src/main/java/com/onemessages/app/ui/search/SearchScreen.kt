@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.model.SearchHit
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.components.EmptyState
import com.onemessages.app.ui.components.SectionHeader
import com.onemessages.app.util.TimeFormatters

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenConversation: (threadId: Long, address: String) -> Unit,
    onMessageContact: (address: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.scope == SearchScope.ALL,
                    onClick = { viewModel.setScope(SearchScope.ALL) },
                    label = { Text(stringResource(R.string.inbox_filter_all)) },
                )
                FilterChip(
                    selected = state.scope == SearchScope.MESSAGES,
                    onClick = { viewModel.setScope(SearchScope.MESSAGES) },
                    label = { Text(stringResource(R.string.search_messages_header)) },
                )
                FilterChip(
                    selected = state.scope == SearchScope.CONTACTS,
                    onClick = { viewModel.setScope(SearchScope.CONTACTS) },
                    label = { Text(stringResource(R.string.search_contacts_header)) },
                )
            }
            when {
                state.query.length < 2 -> EmptyState(
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body),
                    icon = Icons.Rounded.Search,
                )
                state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.messages.isEmpty() && state.contacts.isEmpty() -> EmptyState(
                    title = stringResource(R.string.search_no_results_title),
                    body = stringResource(R.string.search_no_results_body),
                    icon = Icons.Rounded.Search,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (state.contacts.isNotEmpty() && state.scope != SearchScope.MESSAGES) {
                        item { SectionHeader(stringResource(R.string.search_contacts_header)) }
                        items(state.contacts, key = { "contact-${it.contactId}" }) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                supportingContent = { Text(contact.primaryPhone.orEmpty()) },
                                leadingContent = {
                                    Avatar(contact.displayName, contact.photoUri, size = 40.dp)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.messages.isNotEmpty() && state.scope != SearchScope.CONTACTS) {
                        item {
                            SectionHeader(
                                if (state.dateSearchLabel != null) {
                                    stringResource(R.string.search_title) + " · " + state.dateSearchLabel
                                } else {
                                    stringResource(R.string.search_messages_header)
                                }
                            )
                        }
                        items(state.messages, key = { "hit-${it.messageId}-${it.threadId}" }) { hit ->
                            SearchResultRow(
                                hit = hit,
                                onOpen = { onOpenConversation(hit.threadId, hit.address) },
                                onContact = { onMessageContact(hit.address) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onOpen: () -> Unit, onContact: () -> Unit) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Column {
    ListItem(
        headlineContent = { Text(hit.displayName) },
        supportingContent = {
            Text(
                text = highlighted(hit.snippet, hit.matchStart, hit.matchEnd, highlight),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overlineContent = {
            Text(TimeFormatters.relative(hit.timestamp).toString())
        },
        leadingContent = {
            Avatar(hit.displayName, null, size = 40.dp, showPhoto = false)
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(start = 72.dp, bottom = 4.dp)) {
        androidx.compose.material3.TextButton(onClick = onOpen) {
            Text(stringResource(R.string.nav_chats))
        }
        androidx.compose.material3.TextButton(onClick = onContact) {
            Text(stringResource(R.string.contact_message))
        }
    }
    }
}

private fun highlighted(text: String, start: Int, end: Int, background: androidx.compose.ui.graphics.Color): AnnotatedString {
    if (start < 0 || end <= start || start >= text.length) return AnnotatedString(text)
    val safeEnd = end.coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(background = background, fontWeight = FontWeight.Medium), start, safeEnd)
    }
}
