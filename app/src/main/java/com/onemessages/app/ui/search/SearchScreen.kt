@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.model.SearchHit
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.components.EmptyState
import com.onemessages.app.ui.components.SectionHeader
import com.onemessages.app.util.TimeFormatters

/**
 * Full-text search over the message store plus device contacts.
 *
 * Rows are two-tap targets on purpose: tapping the row opens the conversation at the match,
 * while the trailing button starts a message to the sender. That keeps the list calm instead of
 * putting two competing text buttons under every hit.
 */
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
        containerColor = MaterialTheme.colorScheme.background,
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
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.clear),
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchScopeChip(
                    label = stringResource(R.string.inbox_filter_all),
                    selected = state.scope == SearchScope.ALL,
                    onClick = { viewModel.setScope(SearchScope.ALL) },
                )
                SearchScopeChip(
                    label = stringResource(R.string.search_messages_header),
                    selected = state.scope == SearchScope.MESSAGES,
                    onClick = { viewModel.setScope(SearchScope.MESSAGES) },
                )
                SearchScopeChip(
                    label = stringResource(R.string.search_contacts_header),
                    selected = state.scope == SearchScope.CONTACTS,
                    onClick = { viewModel.setScope(SearchScope.CONTACTS) },
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

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (state.contacts.isNotEmpty() && state.scope != SearchScope.MESSAGES) {
                        item { SectionHeader(stringResource(R.string.search_contacts_header)) }
                        items(state.contacts, key = { "contact-${it.contactId}" }) { contact ->
                            SearchContactRow(
                                name = contact.displayName,
                                number = contact.primaryPhone.orEmpty(),
                                photoUri = contact.photoUri,
                                onMessage = {
                                    contact.primaryPhone?.takeIf { it.isNotBlank() }?.let(onMessageContact)
                                },
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
                }
            }
        }
    }
}

@Composable
private fun SearchScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(999.dp),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected),
    )
}

@Composable
private fun SearchContactRow(
    name: String,
    number: String,
    photoUri: String?,
    onMessage: () -> Unit,
) {
    Surface(
        onClick = onMessage,
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = name, photoUri = photoUri, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.Chat,
                contentDescription = stringResource(R.string.contact_message),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onOpen: () -> Unit, onContact: () -> Unit) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Surface(
        onClick = onOpen,
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(hit.displayName, null, size = 42.dp, showPhoto = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hit.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = TimeFormatters.relative(hit.timestamp).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = highlighted(hit.snippet, hit.matchStart, hit.matchEnd, highlight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onContact, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = stringResource(R.string.contact_message),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun highlighted(text: String, start: Int, end: Int, background: Color): AnnotatedString {
    if (start < 0 || end <= start || start >= text.length) return AnnotatedString(text)
    val safeEnd = end.coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(background = background, fontWeight = FontWeight.Medium), start, safeEnd)
    }
}
