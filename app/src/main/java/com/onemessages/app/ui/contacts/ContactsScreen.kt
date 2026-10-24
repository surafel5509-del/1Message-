@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.onemessages.app.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemessages.app.R
import com.onemessages.app.model.DeviceContact
import com.onemessages.app.ui.components.Avatar
import com.onemessages.app.ui.components.EmptyState
import com.onemessages.app.ui.components.PermissionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsRoute(
    onBack: () -> Unit,
    onMessage: (address: String) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<DeviceContact?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.contacts_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
            if (!state.hasPermission) {
                PermissionCard(
                    title = stringResource(R.string.contacts_permission_title),
                    body = stringResource(R.string.contacts_permission_body),
                    actionLabel = stringResource(R.string.onboarding_grant_permissions),
                    onAction = onRequestContactsPermission,
                    secondaryLabel = stringResource(R.string.open_settings),
                    onSecondary = onOpenSettings,
                )
            }
            if (state.contacts.isEmpty() && !state.loading && state.hasPermission) {
                EmptyState(
                    title = stringResource(R.string.contacts_empty_title),
                    body = stringResource(R.string.contacts_empty_body),
                    icon = Icons.Rounded.Person,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.contacts, key = { it.contactId }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.displayName) },
                            supportingContent = {
                                Text(
                                    contact.phones.joinToString(" · ") { it.label?.let { label -> "$label " }.orEmpty() + it.number }
                                )
                            },
                            leadingContent = {
                                Avatar(
                                    name = contact.displayName,
                                    photoUri = contact.photoUri,
                                    size = 44.dp,
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { selected = contact }) {
                                        Icon(
                                            Icons.Rounded.Person,
                                            contentDescription = stringResource(R.string.contact_details),
                                        )
                                    }
                                    IconButton(onClick = { contact.primaryPhone?.let(onMessage) }) {
                                        Icon(
                                            Icons.Rounded.Message,
                                            contentDescription = stringResource(R.string.contact_message),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    selected?.let { contact ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(contact.displayName, contact.photoUri, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(contact.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.contact_multiple_numbers, contact.phones.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                contact.phones.forEach { phone ->
                    ListItem(
                        headlineContent = { Text(phone.number) },
                        supportingContent = phone.label?.let { label -> { Text(label) } },
                        leadingContent = { Icon(Icons.Rounded.Call, contentDescription = null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.number}")))
                                }) {
                                    Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.call))
                                }
                                IconButton(onClick = {
                                    selected = null
                                    onMessage(phone.number)
                                }) {
                                    Icon(Icons.Rounded.Message, contentDescription = stringResource(R.string.contact_message))
                                }
                            }
                        },
                    )
                }
                TextButton(onClick = {
                    val uri = android.content.ContentUris.withAppendedId(
                        android.provider.ContactsContract.Contacts.CONTENT_URI,
                        contact.contactId,
                    )
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) {
                    Text(stringResource(R.string.contact_open_details))
                }
            }
        }
    }
}
