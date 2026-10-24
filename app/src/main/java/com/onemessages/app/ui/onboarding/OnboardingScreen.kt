package com.onemessages.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onemessages.app.R
import com.onemessages.app.model.SmsRoleState
import com.onemessages.app.permissions.PermissionGroup
import com.onemessages.app.ui.components.Avatar

/**
 * First-run flow: what this app is, which permissions it needs and why, and the optional
 * default-SMS role. Every step can be skipped; limitations are stated rather than hidden.
 */
@Composable
fun OnboardingScreen(
    hasSms: Boolean,
    hasContacts: Boolean,
    hasNotifications: Boolean,
    hasPhoneState: Boolean,
    roleState: SmsRoleState,
    onRequestGroup: (PermissionGroup) -> Unit,
    onRequestRole: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name = "1", photoUri = null, size = 56.dp, showPhoto = false)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name_brand),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_permissions_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_permissions_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Rounded.ChatBubble,
                title = stringResource(R.string.onboarding_permission_sms_title),
                body = stringResource(R.string.onboarding_permission_sms_body),
                granted = hasSms,
                required = true,
                onRequest = { onRequestGroup(PermissionGroup.SMS) },
            )
            PermissionRow(
                icon = Icons.Rounded.Contacts,
                title = stringResource(R.string.onboarding_permission_contacts_title),
                body = stringResource(R.string.onboarding_permission_contacts_body),
                granted = hasContacts,
                onRequest = { onRequestGroup(PermissionGroup.CONTACTS) },
            )
            PermissionRow(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.onboarding_permission_notifications_title),
                body = stringResource(R.string.onboarding_permission_notifications_body),
                granted = hasNotifications,
                onRequest = { onRequestGroup(PermissionGroup.NOTIFICATIONS) },
            )
            PermissionRow(
                icon = Icons.Rounded.SimCard,
                title = stringResource(R.string.onboarding_permission_phone_title),
                body = stringResource(R.string.onboarding_permission_phone_body),
                granted = hasPhoneState,
                onRequest = { onRequestGroup(PermissionGroup.PHONE_STATE) },
            )
            if (!hasSms) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onRequestGroup(PermissionGroup.SMS) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_grant_permissions))
                }
            }

            Spacer(Modifier.height(28.dp))
            val isDefault = roleState == SmsRoleState.DEFAULT_SMS_APP
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDefault) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.onboarding_role_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isDefault) {
                            stringResource(R.string.onboarding_role_done)
                        } else {
                            stringResource(R.string.onboarding_role_body)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isDefault) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRequestRole) {
                            Text(stringResource(R.string.onboarding_role_set))
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.onboarding_finish))
            }
            if (!hasSms) {
                Text(
                    text = stringResource(R.string.onboarding_not_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    required: Boolean = false,
    onRequest: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (required) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "•",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.permission_granted),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.onboarding_grant_permissions))
                }
            }
        }
    }
}
